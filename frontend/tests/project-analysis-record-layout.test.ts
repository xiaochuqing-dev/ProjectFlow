import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

test("keeps long analysis record paths inside the content column", () => {
  const source = readFileSync(resolve(process.cwd(), "src/app/project-intelligence/page.tsx"), "utf8");

  assert.match(
    source,
    /className="mb-2 grid grid-cols-\[minmax\(0,1fr\)_auto\] items-start gap-3"/,
  );
  assert.match(source, /className="truncate font-medium text-slate-950"/);
});
