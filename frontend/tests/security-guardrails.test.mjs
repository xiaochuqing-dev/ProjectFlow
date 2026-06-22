import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const auth = readFileSync(join(root, "src/lib/auth.ts"), "utf8");
const nextConfig = readFileSync(join(root, "next.config.ts"), "utf8");

assert.match(auth, /isJwtExpired/, "auth session reader should check JWT expiration");
assert.match(auth, /clearSession\(\)/, "expired or invalid session data should clear localStorage");
assert.match(auth, /payload\.exp/, "JWT exp claim should drive frontend session expiry");

assert.match(nextConfig, /async headers\(\)/, "Next config should define security headers");
assert.match(nextConfig, /Content-Security-Policy/, "Next config should send a CSP header");
assert.match(nextConfig, /default-src 'self'/, "CSP should restrict default sources to self");
assert.match(nextConfig, /frame-ancestors 'none'/, "CSP should prevent clickjacking by default");

console.log("security guardrail checks passed");
