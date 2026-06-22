export type DashboardStep =
  | { kind: "no_project" }
  | { kind: "no_material" }
  | { kind: "no_path" }
  | { kind: "has_pending"; count: number }
  | { kind: "scan_updates" };
