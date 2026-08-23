import express from "express";
import helmet from "helmet";
import { createHash } from "node:crypto";
import { gzipSync, gunzipSync } from "node:zlib";
import { applicationDefault, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { createRemoteJWKSet, jwtVerify } from "jose";
import {
  HttpError,
  assertAllowedOrigin,
  parseScopes,
  publicWalkSummary,
  mcpFirebaseUserId,
  isSubscriberPurchase,
  resolveEntitlement,
  requireConfirmation,
  requireIdempotencyKey,
  requireScope,
  validateMcpTransportHeaders,
  shouldApplyClientWrite
} from "./policy.js";
import { toolDefinitions, MCP_CONTRACT_VERSION } from "./tools.js";

function parseTrainingPayload(raw) {
  if (!raw) return null;
  if (typeof raw === "string") return JSON.parse(raw);
  if (typeof raw.payloadJson === "string") return JSON.parse(raw.payloadJson);
  return raw;
}

async function latestTrainingDocument(userId) {
  const snapshot = await userDocument(userId)
    .collection("trainingState")
    .orderBy("clientUpdatedAtMillis", "desc")
    .limit(1)
    .get();
  return snapshot.docs[0]?.data() ?? null;
}

function trainingSummaryFromPayload(payload) {
  const root = parseTrainingPayload(payload);
  if (!root) return null;
  return {
    schemaVersion: root.schemaVersion ?? 1,
    activeWorkoutSessionId: root.activeWorkoutSessionId ?? null,
    workoutHistoryCount: Array.isArray(root.workoutHistory) ? root.workoutHistory.length : 0,
    supplementCount: Array.isArray(root.supplements) ? root.supplements.length : 0,
    workoutTemplateCount: Array.isArray(root.workoutTemplates) ? root.workoutTemplates.length : 0,
    weeklyDayPlanCount: Array.isArray(root.weeklyDayPlans) ? root.weeklyDayPlans.length : 0
  };
}

function trainingProgramFromPayload(payload) {
  const root = parseTrainingPayload(payload);
  if (!root) return null;
  const templates = Array.isArray(root.workoutTemplates)
    ? root.workoutTemplates.map((template) => ({
        id: template.id,
        name: template.name,
        exerciseCount: Array.isArray(template.exercises) ? template.exercises.length : 0
      }))
    : [];
  const weeklyDayPlans = Array.isArray(root.weeklyDayPlans)
    ? root.weeklyDayPlans.map((plan) => ({
        weekday: plan.weekday,
        templateIds: plan.templateIds ?? [],
        restDay: Boolean(plan.restDay)
      }))
    : [];
  return { workoutTemplates: templates, weeklyDayPlans };
}

const googleCredential = applicationDefault();
if (!getApps().length) initializeApp({ credential: googleCredential });

const firestore = getFirestore();
const app = express();
app.use(helmet());
app.use(express.json({ limit: "2mb" }));

const allowedOrigins = new Set(
  (process.env.ALLOWED_MCP_ORIGINS ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean)
);
const oauthIssuer = process.env.OAUTH_ISSUER;
const oauthAudience = process.env.OAUTH_AUDIENCE ?? "nutrun-mcp";
const jwks = oauthIssuer
  ? createRemoteJWKSet(new URL(`${oauthIssuer.replace(/\/$/, "")}/.well-known/jwks.json`))
  : null;
const subscriptionProducts = new Set([
  "nutrun_ad_free_monthly",
  "nutrun_ad_free_annual"
]);

function bearer(req) {
  const value = req.get("authorization") ?? "";
  if (!value.startsWith("Bearer ")) throw new HttpError(401, "unauthorized");
  return value.slice(7);
}

async function mobileAuth(req, _res, next) {
  try {
    const token = await getAuth().verifyIdToken(bearer(req), true);
    req.principal = { userId: token.uid, scopes: new Set() };
    next();
  } catch (error) {
    next(error instanceof HttpError ? error : new HttpError(401, "invalid_token"));
  }
}

async function mcpAuth(req, _res, next) {
  try {
    assertAllowedOrigin(req.get("origin"), allowedOrigins);
    if (!jwks || !oauthIssuer) throw new HttpError(503, "oauth_not_configured");
    const { payload } = await jwtVerify(bearer(req), jwks, {
      issuer: oauthIssuer,
      audience: oauthAudience
    });
    const userId = mcpFirebaseUserId(
      payload,
      process.env.OAUTH_SUBJECT_IS_FIREBASE_UID === "true"
    );
    const revoked = await firestore.collection("revokedMcpSubjects").doc(hashId(userId)).get();
    if (revoked.exists) throw new HttpError(401, "token_revoked");
    const principal = { userId, oauthSubject: payload.sub, scopes: parseScopes(payload.scope) };
    await enforceRateLimit(principal.userId);
    req.principal = principal;
    next();
  } catch (error) {
    next(error instanceof HttpError ? error : new HttpError(401, "invalid_token"));
  }
}

async function enforceRateLimit(userId) {
  const now = Date.now();
  const reference = firestore.collection("mcpRateLimits").doc(hashId(userId));
  await firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reference);
    const data = snapshot.data();
    const windowStart = data?.windowStartMillis ?? now;
    const inWindow = now - windowStart < 60_000;
    const count = inWindow ? data?.count ?? 0 : 0;
    if (count >= 60) throw new HttpError(429, "rate_limited");
    transaction.set(reference, {
      windowStartMillis: inWindow ? windowStart : now,
      count: count + 1,
      expiresAt: new Date(now + 120_000)
    });
  });
}

function hashId(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function userDocument(userId) {
  return firestore.collection("users").doc(userId);
}

async function idempotentCreate(userId, collection, key, value) {
  requireIdempotencyKey(key);
  const marker = userDocument(userId).collection("idempotency").doc(key);
  return firestore.runTransaction(async (transaction) => {
    const existing = await transaction.get(marker);
    if (existing.exists) return existing.data().result;
    const document = userDocument(userId).collection(collection).doc();
    const result = { id: document.id, created: true };
    transaction.create(document, {
      ...value,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });
    transaction.create(marker, { result, createdAt: FieldValue.serverTimestamp() });
    return result;
  });
}

async function syncOperation(userId, idempotencyKey, body) {
  requireIdempotencyKey(idempotencyKey);
  const allowed = new Set([
    "profile",
    "weightEntries",
    "foodLogs",
    "waterLogs",
    "hydrationPlan",
    "walks",
    "trainingState"
  ]);
  const {
    entityType,
    entityId,
    operation,
    payload,
    clientUpdatedAtMillis
  } = body ?? {};
  if (!allowed.has(entityType)) throw new HttpError(400, "invalid_entity_type");
  if (typeof entityId !== "string" || !entityId || entityId.length > 200) {
    throw new HttpError(400, "invalid_entity_id");
  }
  if (!["UPSERT", "DELETE"].includes(operation)) {
    throw new HttpError(400, "invalid_operation");
  }
  const user = userDocument(userId);
  const marker = user.collection("idempotency").doc(hashId(idempotencyKey));
  const existing = await marker.get();
  if (existing.exists) return existing.data().result;

  const incomingTimestamp = Number(clientUpdatedAtMillis);
  if (!Number.isFinite(incomingTimestamp) || incomingTimestamp <= 0) {
    throw new HttpError(400, "invalid_client_timestamp");
  }
  let normalizedPayload = {
    ...(payload ?? {}),
    clientUpdatedAtMillis: incomingTimestamp
  };
  if (entityType === "walks" && operation === "UPSERT") {
    const route = Array.isArray(payload?.route) ? payload.route : [];
    const storagePath = `users/${userId}/routes/${entityId}.json.gz`;
    await getStorage().bucket().file(storagePath).save(
      gzipSync(Buffer.from(JSON.stringify(route))),
      {
        contentType: "application/json",
        metadata: { contentEncoding: "gzip" },
        resumable: false
      }
    );
    const { route: _route, ...walkSummary } = payload;
    normalizedPayload = {
      ...walkSummary,
      storagePath,
      clientUpdatedAtMillis: incomingTimestamp
    };
  }

  let result = { id: entityId, synced: true, applied: true };
  await firestore.runTransaction(async (transaction) => {
    const repeated = await transaction.get(marker);
    if (repeated.exists) {
      result = repeated.data().result;
      return;
    }
    const target = entityType === "profile" || entityType === "hydrationPlan"
      ? user
      : user.collection(entityType).doc(entityId);
    const targetSnapshot = await transaction.get(target);
    const targetData = targetSnapshot.data();
    const existingTimestamp = entityType === "profile"
      ? targetData?.profile?.clientUpdatedAtMillis ?? 0
      : entityType === "hydrationPlan"
        ? targetData?.hydrationPlan?.clientUpdatedAtMillis ?? 0
        : targetData?.clientUpdatedAtMillis ?? 0;
    if (!shouldApplyClientWrite(existingTimestamp, incomingTimestamp)) {
      result = { id: entityId, synced: true, applied: false, reason: "newer_remote_value" };
      transaction.create(marker, {
        result,
        createdAt: FieldValue.serverTimestamp()
      });
      return;
    }
    if (entityType === "profile") {
      if (operation === "DELETE") {
        transaction.set(user, { profile: FieldValue.delete() }, { merge: true });
      } else {
        transaction.set(
          user,
          { profile: normalizedPayload, updatedAt: FieldValue.serverTimestamp() },
          { merge: true }
        );
      }
    } else if (entityType === "hydrationPlan") {
      transaction.set(
        user,
        {
          hydrationPlan: operation === "DELETE" ? FieldValue.delete() : normalizedPayload,
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );
    } else {
      const document = user.collection(entityType).doc(entityId);
      if (operation === "DELETE") transaction.delete(document);
      else transaction.set(document, {
        ...normalizedPayload,
        updatedAt: FieldValue.serverTimestamp()
      });
    }
    transaction.create(marker, {
      result,
      createdAt: FieldValue.serverTimestamp()
    });
  });
  return result;
}

async function audit(principal, tool, outcome) {
  await firestore.collection("mcpAudit").add({
    userId: principal.userId,
    tool,
    outcome,
    at: FieldValue.serverTimestamp()
  });
}

async function loadWalkRoute(userId, walkId) {
  const walk = await userDocument(userId).collection("walks").doc(walkId).get();
  if (!walk.exists) throw new HttpError(404, "not_found");
  const path = walk.data().storagePath;
  if (!path?.startsWith(`users/${userId}/routes/`)) {
    throw new HttpError(404, "not_found");
  }
  const [buffer] = await getStorage().bucket().file(path).download();
  let content = buffer;
  if (path.endsWith(".gz")) {
    try {
      content = gunzipSync(buffer);
    } catch {
      content = buffer;
    }
  }
  return JSON.parse(content.toString("utf8"));
}

app.get("/.well-known/oauth-protected-resource", (_req, res) => {
  res.json({
    resource: process.env.PUBLIC_BASE_URL,
    authorization_servers: oauthIssuer ? [oauthIssuer] : [],
    scopes_supported: [
      "health.read",
      "logs.write",
      "walks.read",
      "location.read",
      "profile.write",
      "destructive"
    ],
    bearer_methods_supported: ["header"]
  });
});

app.get("/v1/profile", mobileAuth, async (req, res) => {
  const snapshot = await userDocument(req.principal.userId).get();
  res.json(snapshot.data()?.profile ?? null);
});

app.post("/v1/session", mobileAuth, async (req, res) => {
  const user = userDocument(req.principal.userId);
  await firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(user);
    if (!snapshot.exists || !snapshot.data()?.entitlement?.trialStartedAt) {
      transaction.set(
        user,
        {
          entitlement: {
            kind: "TRIAL",
            trialStartedAt: FieldValue.serverTimestamp()
          }
        },
        { merge: true }
      );
    }
  });
  const entitlement = resolveEntitlement((await user.get()).data());
  res.json({
    kind: entitlement.kind,
    trialStartedAtMillis:
      entitlement.trialStartedAtMillis ?? entitlement.trialStartedAt?.toMillis()
  });
});

app.get("/v1/entitlement", mobileAuth, async (req, res) => {
  const entitlement = resolveEntitlement(
    (await userDocument(req.principal.userId).get()).data()
  );
  res.json({
    kind: entitlement.kind,
    expiresAtMillis: entitlement.expiresAt?.toMillis?.() ?? null
  });
});

app.post("/v1/billing/verify", mobileAuth, async (req, res) => {
  const { packageName, productId, purchaseToken } = req.body ?? {};
  const expectedPackage = process.env.ANDROID_PACKAGE_NAME ?? "com.avitoohband.nutrun";
  if (packageName !== expectedPackage || !subscriptionProducts.has(productId)) {
    throw new HttpError(400, "invalid_subscription_product");
  }
  if (typeof purchaseToken !== "string" || purchaseToken.length < 20) {
    throw new HttpError(400, "invalid_purchase_token");
  }
  let accessToken;
  try {
    accessToken = (await googleCredential.getAccessToken()).access_token;
  } catch {
    throw new HttpError(503, "play_verification_unavailable");
  }
  const url = new URL(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/` +
      encodeURIComponent(purchaseToken)
  );
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (!response.ok) {
    throw new HttpError(
      response.status === 404 ? 400 : 503,
      response.status === 404 ? "purchase_not_found" : "play_verification_unavailable"
    );
  }
  const purchase = await response.json();
  const matchingLine = (purchase.lineItems ?? []).find(
    (line) => line.productId === productId
  );
  if (!matchingLine) throw new HttpError(400, "product_mismatch");
  const expiresAt = new Date(matchingLine.expiryTime);
  const subscriber = isSubscriberPurchase(
    purchase.subscriptionState,
    expiresAt.getTime()
  );
  const user = userDocument(req.principal.userId);
  const existing = (await user.get()).data()?.entitlement ?? {};
  await user.set(
    {
      entitlement: {
        ...existing,
        kind: subscriber ? "SUBSCRIBER" : resolveEntitlement({ entitlement: existing }).kind,
        productId,
        purchaseTokenHash: hashId(purchaseToken),
        subscriptionState: purchase.subscriptionState,
        expiresAt,
        verifiedAt: FieldValue.serverTimestamp()
      }
    },
    { merge: true }
  );
  res.json({
    verified: true,
    kind: subscriber ? "SUBSCRIBER" : resolveEntitlement({ entitlement: existing }).kind,
    expiresAtMillis: expiresAt.getTime()
  });
});

app.get("/v1/daily/:date", mobileAuth, async (req, res) => {
  const summary = await dailySummary(req.principal.userId, req.params.date);
  res.json(summary);
});

app.get("/v1/walks/:walkId/route", mobileAuth, async (req, res) => {
  res.json({
    points: await loadWalkRoute(req.principal.userId, req.params.walkId)
  });
});

app.get("/v1/foods/search", mobileAuth, async (req, res) => {
  res.json({ items: await searchFood(String(req.query.q ?? "")) });
});

app.post("/v1/logs/:collection", mobileAuth, async (req, res) => {
  const allowed = new Set(["foodLogs", "waterLogs", "supplementLogs", "workouts"]);
  if (!allowed.has(req.params.collection)) throw new HttpError(404, "not_found");
  const result = await idempotentCreate(
    req.principal.userId,
    req.params.collection,
    req.get("idempotency-key"),
    req.body
  );
  res.status(result.created ? 201 : 200).json(result);
});

app.post("/v1/sync", mobileAuth, async (req, res) => {
  const result = await syncOperation(
    req.principal.userId,
    req.get("idempotency-key"),
    req.body
  );
  res.json(result);
});

app.get("/v1/sync/snapshot", mobileAuth, async (req, res) => {
  const user = userDocument(req.principal.userId);
  const userSnapshot = await user.get();
  const collectionNames = [
    "weightEntries",
    "foodLogs",
    "waterLogs",
    "walks",
    "trainingState"
  ];
  const snapshots = await Promise.all(
    collectionNames.map((name) =>
      user.collection(name).orderBy("clientUpdatedAtMillis", "desc").get()
    )
  );
  const body = {
    profile: userSnapshot.data()?.profile ?? null,
    hydrationPlan: userSnapshot.data()?.hydrationPlan ?? null
  };
  collectionNames.forEach((name, index) => {
    body[name] = snapshots[index].docs.map((document) => ({
      id: document.id,
      ...document.data()
    }));
  });
  res.json(body);
});

app.delete("/v1/account", mobileAuth, async (req, res) => {
  const userId = req.principal.userId;
  await firestore.collection("revokedMcpSubjects").doc(hashId(userId)).set({
    userId,
    revokedAt: FieldValue.serverTimestamp()
  });
  const bucket = getStorage().bucket();
  await bucket.deleteFiles({ prefix: `users/${userId}/` });
  await firestore.recursiveDelete(userDocument(userId));
  await getAuth().revokeRefreshTokens(userId);
  await getAuth().deleteUser(userId);
  res.status(204).end();
});

app.post("/mcp", mcpAuth, async (req, res, next) => {
  try {
    validateMcpTransportHeaders(
      req.get("accept") ?? "",
      req.get("mcp-protocol-version")
    );
  } catch (error) {
    return next(error);
  }
  const request = req.body;
  const respond = (result) => res.json({ jsonrpc: "2.0", id: request.id ?? null, result });
  try {
    if (request.method === "initialize") {
      return respond({
        protocolVersion: "2025-03-26",
        capabilities: { tools: { listChanged: false } },
        serverInfo: { name: "NutRun", version: MCP_CONTRACT_VERSION }
      });
    }
    if (request.method === "notifications/initialized") return res.status(202).end();
    if (request.method === "tools/list") return respond({ tools: toolDefinitions });
    if (request.method !== "tools/call") throw new HttpError(404, "method_not_found");

    const name = request.params?.name;
    const args = request.params?.arguments ?? {};
    const value = await callTool(req.principal, name, args);
    await audit(req.principal, name, "success");
    return respond({ content: [{ type: "text", text: JSON.stringify(value) }] });
  } catch (error) {
    const known = error instanceof HttpError ? error : new HttpError(500, "internal_error");
    await audit(req.principal, request.params?.name ?? request.method, known.code);
    if (known.code === "confirmation_required") {
      return respond({
        isError: true,
        content: [{ type: "text", text: JSON.stringify({ code: known.code }) }]
      });
    }
    next(known);
  }
});

app.get("/mcp", mcpAuth, (_req, res) => {
  res.set("Allow", "POST").status(405).end();
});

app.delete("/mcp", mcpAuth, (_req, res) => {
  res.set("Allow", "POST").status(405).end();
});

async function callTool(principal, name, args) {
  const user = userDocument(principal.userId);
  switch (name) {
    case "get_profile_summary": {
      requireScope(principal.scopes, "health.read");
      return (await user.get()).data()?.profile ?? null;
    }
    case "get_daily_summary":
      requireScope(principal.scopes, "health.read");
      return dailySummary(principal.userId, args.date);
    case "search_food":
      requireScope(principal.scopes, "health.read");
      return { items: await searchFood(args.query) };
    case "log_food":
      requireScope(principal.scopes, "logs.write");
      return idempotentCreate(principal.userId, "foodLogs", args.idempotencyKey, args);
    case "log_water":
      requireScope(principal.scopes, "logs.write");
      return idempotentCreate(principal.userId, "waterLogs", args.idempotencyKey, args);
    case "log_supplement_status":
      requireScope(principal.scopes, "logs.write");
      return idempotentCreate(principal.userId, "supplementLogs", args.idempotencyKey, args);
    case "log_workout":
      requireScope(principal.scopes, "logs.write");
      const workoutDate = args.date ?? args.completedAt?.slice(0, 10);
      return idempotentCreate(principal.userId, "workouts", args.idempotencyKey, {
        name: args.name,
        completedAt: args.completedAt,
        date: workoutDate,
        sessionId: args.sessionId ?? null,
        sets: args.sets ?? []
      });
    case "get_training_summary": {
      requireScope(principal.scopes, "health.read");
      const trainingDocument = await latestTrainingDocument(principal.userId);
      return trainingSummaryFromPayload(trainingDocument);
    }
    case "get_training_program": {
      requireScope(principal.scopes, "health.read");
      const trainingDocument = await latestTrainingDocument(principal.userId);
      return trainingProgramFromPayload(trainingDocument);
    }
    case "list_weight_entries": {
      requireScope(principal.scopes, "health.read");
      const limit = Math.min(Math.max(Number(args.limit ?? 20), 1), 100);
      const weights = await user.collection("weightEntries")
        .orderBy("recordedAtMillis", "desc")
        .limit(limit)
        .get();
      return weights.docs.map((document) => ({ id: document.id, ...document.data() }));
    }
    case "log_weight": {
      requireScope(principal.scopes, "logs.write");
      const recordedAtMillis = args.recordedAt
        ? Date.parse(args.recordedAt)
        : Date.now();
      if (!Number.isFinite(recordedAtMillis)) throw new HttpError(400, "invalid_recorded_at");
      return idempotentCreate(principal.userId, "weightEntries", args.idempotencyKey, {
        weightKg: args.weightKg,
        recordedAtMillis,
        clientUpdatedAtMillis: Date.now()
      });
    }
    case "get_hydration_plan": {
      requireScope(principal.scopes, "health.read");
      return (await user.get()).data()?.hydrationPlan ?? null;
    }
    case "get_reminder_settings": {
      requireScope(principal.scopes, "health.read");
      const reminderSettings = (await user.get()).data()?.reminderSettings ?? null;
      return {
        reminderSettings,
        mobileDeviceLocalNote:
          "Training and supplement reminder toggles may remain device-local on mobile until cloud sync is enabled."
      };
    }
    case "update_reminder_settings": {
      requireScope(principal.scopes, "profile.write");
      requireConfirmation(args);
      const existing = (await user.get()).data()?.reminderSettings ?? {};
      const merged = {
        ...existing,
        ...args.patch,
        clientUpdatedAtMillis: Date.now()
      };
      await user.set({ reminderSettings: merged, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      return { updated: true, reminderSettings: merged };
    }
    case "list_walks": {
      requireScope(principal.scopes, "walks.read");
      const walks = await user.collection("walks").orderBy("startedAt", "desc").limit(100).get();
      return walks.docs.map((document) => publicWalkSummary({ id: document.id, ...document.data() }));
    }
    case "get_walk_summary": {
      requireScope(principal.scopes, "walks.read");
      const walk = await user.collection("walks").doc(args.walkId).get();
      if (!walk.exists) throw new HttpError(404, "not_found");
      return publicWalkSummary({ id: walk.id, ...walk.data() });
    }
    case "get_walk_route": {
      requireScope(principal.scopes, "location.read");
      return loadWalkRoute(principal.userId, args.walkId);
    }
    case "update_profile":
      requireScope(principal.scopes, "profile.write");
      requireConfirmation(args);
      await user.set({ profile: args.patch, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      return { updated: true };
    case "delete_log":
      requireScope(principal.scopes, "destructive");
      requireConfirmation(args);
      if (!["foodLogs", "waterLogs", "supplementLogs", "workouts"].includes(args.collection)) {
        throw new HttpError(400, "invalid_collection");
      }
      await user.collection(args.collection).doc(args.logId).delete();
      return { deleted: true };
    default:
      throw new HttpError(404, "tool_not_found");
  }
}

async function dailySummary(userId, date) {
  const user = userDocument(userId);
  const collections = ["foodLogs", "waterLogs", "supplementLogs", "workouts"];
  const snapshots = await Promise.all(
    collections.map((name) => user.collection(name).where("date", "==", date).get())
  );
  return Object.fromEntries(
    collections.map((name, index) => [name, snapshots[index].docs.map((doc) => ({ id: doc.id, ...doc.data() }))])
  );
}

async function searchFood(query) {
  const normalized = String(query ?? "").trim();
  if (normalized.length < 2) return [];
  const url = new URL("https://world.openfoodfacts.org/cgi/search.pl");
  url.searchParams.set("search_terms", normalized);
  url.searchParams.set("search_simple", "1");
  url.searchParams.set("action", "process");
  url.searchParams.set("json", "1");
  url.searchParams.set("page_size", "20");
  const response = await fetch(url, {
    headers: { "User-Agent": process.env.OPEN_FOOD_FACTS_USER_AGENT ?? "NutRun/0.1 support@example.com" }
  });
  if (!response.ok) throw new HttpError(502, "food_search_unavailable");
  const body = await response.json();
  return (body.products ?? []).map((product) => {
    const servingGrams = Number.parseFloat(product.serving_quantity) || 100;
    const servingFactor = servingGrams / 100;
    return {
      id: product.code,
      name: product.product_name,
      brand: product.brands,
      servingGrams,
      calories: Math.round((product.nutriments?.["energy-kcal_100g"] ?? 0) * servingFactor),
      proteinGrams: (product.nutriments?.proteins_100g ?? 0) * servingFactor,
      carbohydrateGrams: (product.nutriments?.carbohydrates_100g ?? 0) * servingFactor,
      fatGrams: (product.nutriments?.fat_100g ?? 0) * servingFactor
    };
  }).filter((item) => item.name);
}

app.use((error, _req, res, _next) => {
  const known = error instanceof HttpError ? error : new HttpError(500, "internal_error");
  res.status(known.status).json({ error: known.code, message: known.message });
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  console.log(`NutRun service listening on ${port}`);
});
