import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { bytesToBase64Url, base64UrlToBytes, encodeJsonSegment, decodeJsonSegment } from "../src/base64";
import { signJwt, verifyJwt, parseJwt } from "../src/crypto";
import { issueSnapshot, readSnapshot } from "../src/snapshot";
import { Registry } from "../src/registry";
import { getCredential, setCredential, verifyPassword, deleteCredential } from "../src/credentials";
import { SnapshotPayload } from "../src/types";

function fakeKv(): KVNamespace {
  const store = new Map<string, string>();
  return {
    get: async (key: string) => store.get(key) ?? null,
    put: async (key: string, value: string) => { store.set(key, value); },
    delete: async (key: string) => { store.delete(key); },
  } as unknown as KVNamespace;
}

const sampleClaims = (overrides: Partial<SnapshotPayload> = {}): SnapshotPayload => ({
  v: 1,
  sub: "google-123",
  email: "inspector@example.com",
  name: "Inspector",
  role: "USER",
  tier: "FREE",
  effectiveTier: "FREE",
  paymentStatus: "ACTIVE",
  seats: 1,
  team: [],
  iat: Math.floor(Date.now() / 1000),
  exp: Math.floor(Date.now() / 1000) + 900,
  jti: "test-jti",
  ...overrides,
});

test("base64url round-trips arbitrary bytes and JSON segments", () => {
  const bytes = new Uint8Array([0, 1, 2, 250, 251, 252, 253, 254, 255]);
  assert.deepEqual(Array.from(base64UrlToBytes(bytesToBase64Url(bytes))), Array.from(bytes));
  const value = { role: "OWNER", nested: { n: 42 } };
  assert.deepEqual(decodeJsonSegment(encodeJsonSegment(value)), value);
});

test("RS256 JWTs verify with the public key and reject tampering", async () => {
  const { privateKey, publicKey } = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"]
  ) as CryptoKeyPair;
  const token = await signJwt({ sub: "user", exp: 9999999999 }, privateKey);
  assert.equal((await verifyJwt<{ sub: string }>(token, publicKey))?.sub, "user");

  const tampered = token.slice(0, -2) + (token.endsWith("aa") ? "bb" : "aa");
  assert.equal(await verifyJwt(tampered, publicKey), null);
  assert.equal(parseJwt(token).payload.sub, "user");
});

test("signed snapshots are accepted while valid and rejected when expired", async () => {
  // Use the same PEM -> base64 DER format the Worker secret uses.
  const pair = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" },
  });
  const strip = (pem: string) => pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const env = {
    SIGNING_KEY_PKCS8_BASE64: strip(pair.privateKey),
    SIGNING_KEY_SPKI_BASE64: strip(pair.publicKey),
  };
  const token = await issueSnapshot(sampleClaims(), env);
  const payload = await readSnapshot(token, env);
  assert.equal(payload?.role, "USER");
  assert.equal(payload?.email, "inspector@example.com");

  const expired = await issueSnapshot(sampleClaims({ exp: Math.floor(Date.now() / 1000) - 10 }), env);
  assert.equal(await readSnapshot(expired, env), null);
  assert.ok(await readSnapshot(expired, env, { expiredGraceSeconds: 3600 }));
});

test("registry is authoritative: owners come from env, roles cannot escalate to OWNER", async () => {
  const kv = fakeKv();
  const registry = new Registry(kv, [" OWNER@EXAMPLE.COM "]);
  assert.equal(await registry.roleFor("owner@example.com"), "OWNER");
  assert.equal(await registry.roleFor("user@example.com"), "USER");

  await registry.setRole("user@example.com", "MANAGER", "owner@example.com");
  assert.equal(await registry.roleFor("user@example.com"), "MANAGER");

  // Even a poisoned KV value cannot create an OWNER outside the configured allowlist.
  const digest = Array.from(
    new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode("user@example.com")))
  ).map((b) => b.toString(16).padStart(2, "0")).join("");
  await kv.put(`role:${digest}`, JSON.stringify({ role: "OWNER" }));
  assert.equal(await registry.roleFor("user@example.com"), "USER");
});

test("inspector credentials are salted, verifiable and removable", async () => {
  const kv = fakeKv();
  const record = await setCredential(kv, "inspector@example.com", "correct horse battery", "MANAGER");
  assert.equal(record.role, "MANAGER");
  assert.ok(record.salt.length > 10);
  assert.ok(record.hash.length > 10);

  const stored = await getCredential(kv, "inspector@example.com");
  assert.ok(stored);
  assert.equal(await verifyPassword(stored!, "correct horse battery"), true);
  assert.equal(await verifyPassword(stored!, "wrong password"), false);
  assert.equal(await verifyPassword(stored!, "correct horse battery2"), false);

  const other = await setCredential(kv, "other@example.com", "correct horse battery", "USER");
  assert.notEqual(other.salt, stored!.salt);
  assert.notEqual(other.hash, stored!.hash);

  await deleteCredential(kv, "inspector@example.com");
  assert.equal(await getCredential(kv, "inspector@example.com"), null);
});

test("OWNER can never be created through the credential endpoint", async () => {
  const kv = fakeKv();
  const record = await setCredential(kv, "user@example.com", "some-long-password", "OWNER");
  assert.equal(record.role, "USER");
});

test("registry entitlement defaults to FREE and honours stored values", async () => {
  const kv = fakeKv();
  const registry = new Registry(kv, []);
  assert.deepEqual(await registry.entitlementFor("user@example.com"), {
    tier: "FREE", paymentStatus: "ACTIVE", seats: 1,
  });
  await registry.setEntitlement("user@example.com", { tier: "ENTERPRISE", paymentStatus: "ACTIVE", seats: 12 }, "owner");
  assert.deepEqual(await registry.entitlementFor("user@example.com"), {
    tier: "ENTERPRISE", paymentStatus: "ACTIVE", seats: 12,
  });
});
