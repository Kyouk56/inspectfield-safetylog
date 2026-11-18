import { importJwk, parseJwt } from "./crypto";

/** Verifies Google-issued OpenID Connect ID tokens against Google's published JWKS. */

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const ISSUERS = new Set(["https://accounts.google.com", "accounts.google.com"]);
const CLOCK_SKEW_SECONDS = 60;

interface GoogleIdClaims {
  iss?: string;
  aud?: string | string[];
  exp?: number;
  iat?: number;
  nbf?: number;
  sub?: string;
  email?: string;
  email_verified?: boolean;
  nonce?: string;
  azp?: string;
  name?: string;
}

export interface VerifiedIdentity {
  email: string;
  subject: string;
  name: string | null;
}

export class UnauthorizedError extends Error {}

type Jwk = JsonWebKey & { kid?: string; alg?: string; use?: string };

let jwksCache: { keys: Map<string, Jwk>; expiresAt: number } | null = null;

async function googleKeys(): Promise<Map<string, Jwk>> {
  const now = Date.now();
  if (jwksCache && jwksCache.expiresAt > now) return jwksCache.keys;

  const response = await fetch(GOOGLE_JWKS_URL, {
    headers: { "User-Agent": "safetylog-auth-worker/1.0" },
    cf: { cacheTtl: 3600 },
  } as RequestInit);
  if (!response.ok) throw new Error(`Unable to fetch Google JWKS (${response.status})`);

  const document = (await response.json()) as { keys?: Jwk[] };
  const keys = new Map<string, JsonWebKey>();
  for (const key of document.keys ?? []) {
    if (key.kid) keys.set(key.kid, key);
  }
  if (keys.size === 0) throw new Error("Google JWKS contained no usable keys");

  jwksCache = { keys, expiresAt: now + 60 * 60 * 1000 };
  return keys;
}

/**
 * Verify signature, issuer, audience, expiry and (when supplied) nonce.
 * Throws UnauthorizedError for any authentication failure.
 */
export async function verifyGoogleIdToken(
  idToken: string,
  audience: string,
  expectedNonce?: string
): Promise<VerifiedIdentity> {
  let parsed: ReturnType<typeof parseJwt<GoogleIdClaims>>;
  try {
    parsed = parseJwt<GoogleIdClaims>(idToken);
  } catch {
    throw new UnauthorizedError("Malformed ID token");
  }
  if (parsed.header.alg !== "RS256" || !parsed.header.kid) {
    throw new UnauthorizedError("Unsupported ID token algorithm");
  }

  const jwk = (await googleKeys()).get(parsed.header.kid);
  if (!jwk) throw new UnauthorizedError("Unknown ID token key id");

  let valid = false;
  try {
    const key = await importJwk(jwk);
    valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      parsed.signature,
      new TextEncoder().encode(parsed.signingInput)
    );
  } catch {
    valid = false;
  }
  if (!valid) throw new UnauthorizedError("Invalid ID token signature");

  const claims = parsed.payload;
  const now = Math.floor(Date.now() / 1000);
  if (!claims.iss || !ISSUERS.has(claims.iss)) throw new UnauthorizedError("Invalid token issuer");

  const audiences = Array.isArray(claims.aud) ? claims.aud : claims.aud ? [claims.aud] : [];
  if (!audiences.includes(audience)) throw new UnauthorizedError("Token audience mismatch");

  if (!claims.exp || claims.exp + CLOCK_SKEW_SECONDS <= now) throw new UnauthorizedError("Token expired");
  if (claims.iat && claims.iat - CLOCK_SKEW_SECONDS > now) throw new UnauthorizedError("Token issued in the future");
  if (claims.nbf && claims.nbf > now + CLOCK_SKEW_SECONDS) throw new UnauthorizedError("Token not yet valid");
  if (claims.email_verified !== true || !claims.email) throw new UnauthorizedError("Email not verified");
  if (expectedNonce && claims.nonce !== expectedNonce) throw new UnauthorizedError("Nonce mismatch");

  return {
    email: claims.email.trim().toLowerCase(),
    subject: claims.sub ?? claims.email,
    name: claims.name ?? null,
  };
}
