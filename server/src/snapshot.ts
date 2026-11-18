import { getSigningKeyPair, signJwt, verifyJwt } from "./crypto";
import { SnapshotPayload, SUBSCRIPTION_TIERS, USER_ROLES } from "./types";

/** Issues and validates the signed authorization snapshot consumed by the app. */

export interface SnapshotEnv {
  SIGNING_KEY_PKCS8_BASE64: string;
  SIGNING_KEY_SPKI_BASE64: string;
}

export async function issueSnapshot(claims: SnapshotPayload, env: SnapshotEnv): Promise<string> {
  const { privateKey } = await getSigningKeyPair(env.SIGNING_KEY_PKCS8_BASE64, env.SIGNING_KEY_SPKI_BASE64);
  return signJwt(claims as unknown as Record<string, unknown>, privateKey);
}

export interface ReadOptions {
  /** Allow a still-valid-signature token whose exp has passed, up to this many seconds. */
  expiredGraceSeconds?: number;
}

/** Verify signature and basic shape; optionally accept recently expired tokens for refresh. */
export async function readSnapshot(
  token: string,
  env: SnapshotEnv,
  options: ReadOptions = {}
): Promise<SnapshotPayload | null> {
  const { publicKey } = await getSigningKeyPair(env.SIGNING_KEY_PKCS8_BASE64, env.SIGNING_KEY_SPKI_BASE64);
  const payload = await verifyJwt<SnapshotPayload>(token, publicKey);
  if (!payload || payload.v !== 1 || !payload.email || !payload.sub) return null;
  if (!USER_ROLES.includes(payload.role) || !SUBSCRIPTION_TIERS.includes(payload.tier)) return null;

  const now = Math.floor(Date.now() / 1000);
  const grace = options.expiredGraceSeconds ?? 0;
  if (typeof payload.exp !== "number" || payload.exp + grace <= now) return null;
  return payload;
}
