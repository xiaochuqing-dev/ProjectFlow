import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const settings = readFileSync(join(root, "src/app/settings/page.tsx"), "utf8");

assert.match(settings, /mergeSavedProvider/, "settings page should merge returned providers into local state");
assert.match(settings, /current\.some\(\(item\) => item\.id === provider\.id\)/, "settings page should detect an existing provider id");
assert.doesNotMatch(settings, /setProviders\(\(current\) => \[provider, \.\.\.current\]\)/, "settings page should not blindly prepend saved providers after duplicate saves");

console.log("settings provider dedup checks passed");
