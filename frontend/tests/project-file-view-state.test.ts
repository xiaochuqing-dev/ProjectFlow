import assert from "node:assert/strict";
import test from "node:test";

import { updateProjectFileViewSearch } from "../src/lib/project-file-view-state";

test("persists module, file and query in the URL without dropping other parameters", () => {
  const search = updateProjectFileViewSearch(
    new URLSearchParams("source=dashboard&module=backend"),
    {
      module: "frontend",
      file: "frontend/src/app/dashboard/page.tsx",
      query: "dashboard",
    },
  );

  assert.equal(
    search,
    "source=dashboard&module=frontend&file=frontend%2Fsrc%2Fapp%2Fdashboard%2Fpage.tsx&q=dashboard",
  );
});

test("removes empty view state values from the URL", () => {
  const search = updateProjectFileViewSearch(
    new URLSearchParams("module=backend&file=backend%2Fpom.xml&q=pom"),
    { module: "", file: "", query: "" },
  );

  assert.equal(search, "");
});
