export type DashboardStep =
  | { kind: "no_project" }
  | { kind: "no_material" }
  | { kind: "no_path" }
  | { kind: "has_facts"; count: number; attentionCount: number }
  | { kind: "scan_updates" };
