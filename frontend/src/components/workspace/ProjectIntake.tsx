"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { FolderOpen, GitBranch, FileArchive, FileText, Plus } from "lucide-react";
import { createProject, getProjectMemory, saveProjectLocalPath, importProjectZip, createTextMaterial, refreshProjectHistory, type Project } from "@/lib/api";
import { readSession } from "@/lib/auth";

export function ProjectIntake({ existing, demo = false }: { existing?: Project; demo?: boolean }) {
  const router = useRouter();
  const [mode, setMode] = useState("local"), [name, setName] = useState(existing?.name ?? ""), [description, setDescription] = useState(existing?.description ?? "");
  const [path, setPath] = useState(""), [repoUrl, setRepoUrl] = useState(existing?.repoUrl ?? ""), [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [created, setCreated] = useState<Project | null>(existing ?? null);
  const pending = useRef(false);
  useEffect(() => {
    if (!existing || demo) return;
    let active = true;
    getProjectMemory(readSession().accessToken, existing.id).then(memory => { if (active) setPath(memory.localProjectPath ?? ""); }).catch(() => {});
    return () => { active = false; };
  }, [existing, demo]);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (pending.current || demo) return;
    pending.current = true; setBusy(true); setError("");
    const token = readSession().accessToken;
    try {
      let project = created;
      if (mode === "zip") {
        if (!file) throw new Error("请选择 ZIP 文件。");
        project = (await importProjectZip(token, file, project?.id)).project;
        setCreated(project);
      } else {
        if (!project) {
          project = await createProject(token, { name: name.trim(), description: description.trim(), repoUrl: repoUrl.trim(),
            status: "BUILDING", techStack: [], startDate: new Date().toISOString().slice(0, 10), endDate: null });
          setCreated(project);
        }
        if (["local", "github"].includes(mode) && path.trim()) await saveProjectLocalPath(token, project.id, path.trim());
        if (mode === "text") await createTextMaterial(token, project.id, "OTHER", text);
      }
      if (!project) return;
      if (path.trim() || mode === "zip" || mode === "text") await refreshProjectHistory(token, project.id);
      router.push(`/workspace/current?project=${project.id}`);
    } catch (e) { setError(e instanceof Error ? e.message : "接入未完成，已创建的项目会保留，可以重试。"); }
    finally { pending.current = false; setBusy(false); }
  }
  const modes = [{ id: "local", label: "本地项目", icon: FolderOpen }, { id: "github", label: "GitHub 项目", icon: GitBranch },
    { id: "zip", label: "导入 ZIP", icon: FileArchive }, { id: "text", label: "文本材料", icon: FileText }, { id: "new", label: "新建项目", icon: Plus }];
  return <section className="pf-intake">
    <div className="pf-history-lead"><h2>{existing ? "接入项目材料" : "从真实材料开始理解项目"}</h2><p>保存项目后，读取过程会显示实际任务阶段。缺少历史或计划时，允许保持未知。</p></div>
    <div className="pf-segmented pf-intake-modes" aria-label="项目接入方式">{modes.map(({ id, label, icon: Icon }) => <button key={id} type="button" className={mode === id ? "active" : ""} aria-pressed={mode === id} disabled={busy} onClick={() => setMode(id)}><Icon size={16}/>{label}</button>)}</div>
    <form className="pf-intake-form pf-panel" onSubmit={submit}>
      {mode !== "zip" && !created && <><label>项目名称<input value={name} onChange={e => setName(e.target.value)} required maxLength={120} disabled={busy}/></label><label>项目说明 · 你明确提供的内容<textarea value={description} onChange={e => setDescription(e.target.value)} placeholder="这个项目是什么？也可以先留空。" disabled={busy}/></label></>}
      {mode === "github" && <><label>GitHub 仓库地址<input value={repoUrl} onChange={e => setRepoUrl(e.target.value)} type="url" required disabled={busy || !!created} placeholder="https://github.com/owner/repository"/></label><p className="pf-notice">GitHub 补充 PR、审查和合并状态。完整代码与历史读取使用已在本机的目录；这里不会克隆或修改远程仓库。</p></>}
      {["local", "github"].includes(mode) && <label>本地项目目录{mode === "github" ? "（可稍后绑定）" : ""}<input aria-label="本地项目目录" value={path} onChange={e => setPath(e.target.value)} required={mode === "local"} disabled={busy} placeholder="粘贴已有项目目录"/><small>只读项目材料，ProjectFlow 数据单独保存。本地路径仅用于接入。</small></label>}
      {mode === "zip" && <label>项目 ZIP<input type="file" accept=".zip" onChange={e => setFile(e.target.files?.[0] ?? null)} required disabled={busy}/><small>复用现有 ZIP 导入；源码目录不受影响。不含 Git 的 ZIP 没有分支历史。</small></label>}
      {mode === "text" && <label>项目材料<textarea value={text} onChange={e => setText(e.target.value)} required disabled={busy} placeholder="粘贴项目说明、记录或明确写过的计划"/></label>}
      {created && !existing && <p className="pf-notice">项目“{created.name}”已保存，重试会继续接入这个项目。</p>}
      {error && <p role="alert" className="pf-notice">{error}</p>}
      <div className="pf-intake-actions"><button className="pf-button pf-primary" disabled={busy || demo}>{busy ? "正在接入项目…" : existing ? "保存来源并读取" : "添加项目并继续"}</button><Link className="pf-text-link" href="/workspace/projects">返回项目库</Link></div>
      {demo && <p className="pf-notice">示例模式不会保存材料。请打开真实项目库后接入。</p>}
    </form>
  </section>;
}
