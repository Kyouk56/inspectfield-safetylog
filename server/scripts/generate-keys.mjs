// Generates the RS256 signing keypair shared by the Worker and the Android app.
// Run: node scripts/generate-keys.mjs
import { generateKeyPairSync } from "node:crypto";

const { privateKey, publicKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "spki", format: "pem" },
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
});

const derBase64 = (pem) => pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");

console.log("=== 1. Cloudflare Worker secret (SIGNING_KEY_PKCS8_BASE64) ===");
console.log(derBase64(privateKey));
console.log("\nSet it with: wrangler secret put SIGNING_KEY_PKCS8_BASE64");
console.log("\n=== 2. Android BuildConfig value (AUTH_PUBLIC_KEY_SPKI_BASE64) ===");
console.log(derBase64(publicKey));
console.log("\nAdd to gradle.properties as AUTH_PUBLIC_KEY_SPKI_BASE64=<value above>");
console.log("\nKeep the private key out of git. The public key is safe to embed in the app.");
