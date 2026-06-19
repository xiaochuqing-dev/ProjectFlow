export type ProjectFileViewStatePatch = {
  module?: string;
  file?: string;
  query?: string;
};

export function updateProjectFileViewSearch(
  current: URLSearchParams,
  patch: ProjectFileViewStatePatch,
) {
  const next = new URLSearchParams(current.toString());
  updateParam(next, "module", patch.module);
  updateParam(next, "file", patch.file);
  updateParam(next, "q", patch.query);
  return next.toString();
}

function updateParam(search: URLSearchParams, key: string, value: string | undefined) {
  if (value === undefined) {
    return;
  }
  if (value.trim()) {
    search.set(key, value.trim());
  } else {
    search.delete(key);
  }
}
