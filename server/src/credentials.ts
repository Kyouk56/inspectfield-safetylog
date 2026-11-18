import { bytesToBase64Url, base64UrlToBytes } from "./base64";
import { UserRole, USER_ROLES } from "./types";

/**
 * Server-side inspector credentials. Passwords are stored as PBKDF2-SHA256 hashes with a
 * per-account random salt; the app never holds a privileged credential.
 */

const PBKDF2_ITERATIONS = 150_000;
const SALT_BYTES = 16;

export interface CredentialRecord {
  salt: string;
  hash: string;
  role: UserRole;
}

async function emailKey(email: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(email.trim().toLowerCase()));
  const hex = Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `cred:${hex}`;
}

async function deriveHash(password: string, salt: Uint8Array): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: PBKDF2_ITERATIONS, hash: "SHA-256" },
    key,
    256
  );
  return bytesToBase64Url(new Uint8Array(bits));
}

export async function getCredential(kv: KVNamespace, email: string): Promise<CredentialRecord | null> {
  const raw = await kv.get(await emailKey(email));
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as CredentialRecord;
    if (!parsed.salt || !parsed.hash || !USER_ROLES.includes(parsed.role)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export async function verifyPassword(record: CredentialRecord, password: string): Promise<boolean> {
  const expected = await deriveHash(password, base64UrlToBytes(record.salt));
  const a = base64UrlToBytes(expected);
  const b = base64UrlToBytes(record.hash);
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i]! ^ b[i]!;
  return diff === 0;
}

export async function setCredential(
  kv: KVNamespace,
  email: string,
  password: string,
  role: UserRole
): Promise<CredentialRecord> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
  const record: CredentialRecord = {
    salt: bytesToBase64Url(salt),
    hash: await deriveHash(password, salt),
    role: role === "OWNER" ? "USER" : role,
  };
  await kv.put(await emailKey(email), JSON.stringify(record));
  return record;
}

export async function deleteCredential(kv: KVNamespace, email: string): Promise<void> {
  await kv.delete(await emailKey(email));
}
