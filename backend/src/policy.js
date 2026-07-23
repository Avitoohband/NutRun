export class HttpError extends Error {
  constructor(status, code, message = code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function assertAllowedOrigin(origin, allowedOrigins) {
  if (!origin) return;
  if (!allowedOrigins.has(origin)) {
    throw new HttpError(403, "origin_not_allowed");
  }
}

export function requireScope(scopes, required) {
  if (!scopes.has(required)) {
    throw new HttpError(403, "insufficient_scope", `Required scope: ${required}`);
  }
}

export function requireConfirmation(argumentsValue) {
  if (argumentsValue?.confirmed !== true) {
    throw new HttpError(409, "confirmation_required");
  }
}

export function requireIdempotencyKey(value) {
  if (typeof value !== "string" || value.length < 12 || value.length > 200) {
    throw new HttpError(400, "idempotency_key_required");
  }
  return value;
}

export function publicWalkSummary(walk) {
  const {
    route,
    routePoints,
    encodedPolyline,
    storagePath,
    ...summary
  } = walk ?? {};
  return summary;
}

export function parseScopes(value) {
  if (Array.isArray(value)) return new Set(value);
  if (typeof value !== "string") return new Set();
  return new Set(value.split(/\s+/).filter(Boolean));
}

export function mcpFirebaseUserId(payload, trustSubject = false) {
  if (!payload?.sub) throw new HttpError(401, "invalid_token");
  const userId = payload.firebase_uid ?? (trustSubject ? payload.sub : null);
  if (!userId) throw new HttpError(401, "firebase_uid_claim_required");
  return userId;
}

export function validateMcpTransportHeaders(accept, protocolVersion) {
  if (
    typeof accept !== "string" ||
    !accept.includes("application/json") ||
    !accept.includes("text/event-stream")
  ) {
    throw new HttpError(406, "not_acceptable");
  }
  if (protocolVersion && protocolVersion !== "2025-03-26") {
    throw new HttpError(400, "unsupported_protocol_version");
  }
}

export function resolveEntitlement(data, now = Date.now()) {
  const entitlement = data?.entitlement ?? {};
  const expiry = entitlement.expiresAt?.toMillis?.() ?? 0;
  if (entitlement.kind === "SUBSCRIBER" && expiry > now) {
    return { ...entitlement, kind: "SUBSCRIBER" };
  }
  const trialStartedAtMillis = entitlement.trialStartedAt?.toMillis?.() ?? now;
  const trialActive = now < trialStartedAtMillis + 30 * 24 * 60 * 60 * 1000;
  return {
    ...entitlement,
    kind: trialActive ? "TRIAL" : "FREE_AD_SUPPORTED",
    trialStartedAtMillis
  };
}

export function isSubscriberPurchase(subscriptionState, expiryMillis, now = Date.now()) {
  return new Set([
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"
  ]).has(subscriptionState) &&
    Number.isFinite(expiryMillis) &&
    expiryMillis > now;
}

export function shouldApplyClientWrite(existingTimestamp, incomingTimestamp) {
  return Number(existingTimestamp ?? 0) <= Number(incomingTimestamp);
}
