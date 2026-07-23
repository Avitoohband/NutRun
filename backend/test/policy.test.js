import test from "node:test";
import assert from "node:assert/strict";
import {
  HttpError,
  assertAllowedOrigin,
  mcpFirebaseUserId,
  isSubscriberPurchase,
  publicWalkSummary,
  resolveEntitlement,
  requireConfirmation,
  requireIdempotencyKey,
  requireScope,
  validateMcpTransportHeaders,
  shouldApplyClientWrite
} from "../src/policy.js";

test("walk summaries never expose route material", () => {
  assert.deepEqual(
    publicWalkSummary({
      id: "walk-1",
      distanceMeters: 1000,
      routePoints: [{ latitude: 1, longitude: 2 }],
      encodedPolyline: "secret",
      storagePath: "users/u/routes/walk-1.json"
    }),
    { id: "walk-1", distanceMeters: 1000 }
  );
});

test("location route needs its separately granted scope", () => {
  assert.throws(
    () => requireScope(new Set(["walks.read"]), "location.read"),
    (error) => error instanceof HttpError && error.code === "insufficient_scope"
  );
});

test("destructive operations require explicit confirmation", () => {
  assert.throws(
    () => requireConfirmation({ confirmed: false }),
    (error) => error instanceof HttpError && error.code === "confirmation_required"
  );
  assert.doesNotThrow(() => requireConfirmation({ confirmed: true }));
});

test("writes require a useful idempotency key", () => {
  assert.throws(() => requireIdempotencyKey("short"), HttpError);
  assert.equal(requireIdempotencyKey("request-123456789"), "request-123456789");
});

test("unknown browser origins are rejected", () => {
  assert.throws(
    () => assertAllowedOrigin("https://unknown.example", new Set(["https://claude.ai"])),
    (error) => error instanceof HttpError && error.code === "origin_not_allowed"
  );
});

test("MCP identity must be explicitly linked to a Firebase account", () => {
  assert.equal(
    mcpFirebaseUserId({ sub: "oauth-user", firebase_uid: "firebase-user" }),
    "firebase-user"
  );
  assert.throws(
    () => mcpFirebaseUserId({ sub: "oauth-user" }),
    (error) => error instanceof HttpError && error.code === "firebase_uid_claim_required"
  );
  assert.equal(mcpFirebaseUserId({ sub: "same-user" }, true), "same-user");
});

test("Streamable HTTP clients advertise both supported response formats", () => {
  assert.doesNotThrow(() =>
    validateMcpTransportHeaders(
      "application/json, text/event-stream",
      "2025-03-26"
    )
  );
  assert.throws(
    () => validateMcpTransportHeaders("application/json"),
    (error) => error instanceof HttpError && error.code === "not_acceptable"
  );
});

test("expired subscriptions fall back to the original trial clock", () => {
  const day = 24 * 60 * 60 * 1000;
  const now = Date.UTC(2026, 6, 23);
  const timestamp = (millis) => ({ toMillis: () => millis });
  assert.equal(
    resolveEntitlement({
      entitlement: {
        kind: "SUBSCRIBER",
        expiresAt: timestamp(now - day),
        trialStartedAt: timestamp(now - 31 * day)
      }
    }, now).kind,
    "FREE_AD_SUPPORTED"
  );
  assert.equal(
    resolveEntitlement({
      entitlement: {
        kind: "SUBSCRIBER",
        expiresAt: timestamp(now + day),
        trialStartedAt: timestamp(now - 31 * day)
      }
    }, now).kind,
    "SUBSCRIBER"
  );
});

test("Play entitlement accepts active and grace states only before expiry", () => {
  const now = Date.UTC(2026, 6, 23);
  assert.equal(isSubscriberPurchase("SUBSCRIPTION_STATE_ACTIVE", now + 1, now), true);
  assert.equal(
    isSubscriberPurchase("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", now + 1, now),
    true
  );
  assert.equal(isSubscriberPurchase("SUBSCRIPTION_STATE_CANCELED", now + 1, now), false);
  assert.equal(isSubscriberPurchase("SUBSCRIPTION_STATE_ACTIVE", now - 1, now), false);
});

test("sync conflict policy keeps the newest client timestamp", () => {
  assert.equal(shouldApplyClientWrite(100, 101), true);
  assert.equal(shouldApplyClientWrite(100, 100), true);
  assert.equal(shouldApplyClientWrite(101, 100), false);
});
