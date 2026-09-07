import type { ReactNode } from "react";
import "./workspace.css";

export const metadata = {
  title: "ProjectFlow · 项目工作区",
  icons: { icon: "/workspace/mark.svg" },
};
export default function WorkspaceLayout({ children }: { children: ReactNode }) {
  return children;
}
