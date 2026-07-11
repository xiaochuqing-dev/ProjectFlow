import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const auth = readFileSync(join(root, "src/lib/auth.ts"), "utf8");
const api = readFileSync(join(root, "src/lib/api.ts"), "utf8");
const nextConfig = readFileSync(join(root, "next.config.ts"), "utf8");

assert.match(auth, /LOCAL_SESSION/, "local single-user mode should use a fixed non-secret session marker");
assert.match(auth, /本地单用户模式/, "auth helper should explain the local-only session boundary");
assert.doesNotMatch(auth, /localStorage/, "local single-user mode should not persist credentials in localStorage");

assert.match(nextConfig, /async headers\(\)/, "Next config should define security headers");
assert.match(nextConfig, /Content-Security-Policy/, "Next config should send a CSP header");
assert.match(nextConfig, /default-src 'self'/, "CSP should restrict default sources to self");
assert.match(nextConfig, /frame-ancestors 'none'/, "CSP should prevent clickjacking by default");

assert.match(api, /function apiBaseUrl\(\)/, "API client should resolve the local backend URL at runtime");
assert.match(api, /window\.location\.hostname/, "API client should match the frontend hostname for local embedded mode");
assert.doesNotMatch(api, /const API_BASE_URL = .*localhost:8080/, "API client should not hard-code localhost as the only backend host");

console.log("security guardrail checks passed");
