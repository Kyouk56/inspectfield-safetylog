import { base64ToBytes, decodeJsonSegment, encodeJsonSegment, utf8 } from "./base64";

/** RS256 JWT helpers for the signed authorization snapshot. */

const RSA_ALGORITHM: SubtleCryptoImportKeyAlgorithm = {
  name: "RSASSA-PKCS1-v1_5",
  hash: "SHA-256",
};

let cachedKeyPair: { privateKey: CryptoKey; publicKey: CryptoKey } | null = null;
let cachedKeyMaterial = "";

/**
 * Import (once per isolate) the RSA keypair supplied as base64 DER.
 * The public key is a separate value because Web Crypto runtimes do not reliably
 * allow exporting an SPKI public key from an imported PKCS#8 private key.
 */
export async function getSigningKeyPair(privateDerBase64: string, publicDerBase64: string): Promise<{ privateKey: CryptoKey; publicKey: CryptoKey }> {
  const material = `${privateDerBase64}:${publicDerBase64}`;
  if (cachedKeyPair && cachedKeyMaterial === material) return cachedKeyPair;
  const privateKey = await crypto.subtle.importKey("pkcs8", base64ToBytes(privateDerBase64), RSA_ALGORITHM, false, ["sign"]);
  const publicKey = await crypto.subtle.importKey("spki", base64ToBytes(publicDerBase64), RSA_ALGORITHM, false, ["verify"]);
  cachedKeyPair = { privateKey, publicKey };
  cachedKeyMaterial = material;
  return cachedKeyPair;
}

/** Sign a JSON payload into a compact RS256 JWT. */
export async function signJwt(payload: Record<string, unknown>, privateKey: CryptoKey): Promise<string> {
  const header = encodeJsonSegment({ alg: "RS256", typ: "JWT" });
  const body = encodeJsonSegment(payload);
  const signingInput = `${header}.${body}`;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, utf8(signingInput));
  let binary = "";
  for (const byte of new Uint8Array(signature)) binary += String.fromCharCode(byte);
  return `${signingInput}.${btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")}`;
}

export interface JwtLike<T> {
  header: { alg?: string; typ?: string; kid?: string };
  payload: T;
  signature: Uint8Array;
  signingInput: string;
}

/** Split a compact JWT without verifying it. */
export function parseJwt<T>(token: string): JwtLike<T> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("malformed JWT");
  const [header, body, signature] = parts as [string, string, string];
  return {
    header: decodeJsonSegment<JwtLike<T>["header"]>(header),
    payload: decodeJsonSegment<T>(body),
    signature: base64UrlToUint8(signature),
    signingInput: `${header}.${body}`,
  };
}

function base64UrlToUint8(value: string): Uint8Array {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (value.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** Verify an RS256 JWT's signature with a public key. Returns null on any failure. */
export async function verifyJwt<T>(token: string, publicKey: CryptoKey): Promise<T | null> {
  try {
    const parsed = parseJwt<T>(token);
    if (parsed.header.alg !== "RS256") return null;
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, parsed.signature, utf8(parsed.signingInput));
    return valid ? parsed.payload : null;
  } catch {
    return null;
  }
}

/** Import a JSON Web Key (Google JWKS entry) for signature verification. */
export async function importJwk(jwk: JsonWebKey): Promise<CryptoKey> {
  return crypto.subtle.importKey("jwk", jwk, RSA_ALGORITHM, false, ["verify"]);
}
