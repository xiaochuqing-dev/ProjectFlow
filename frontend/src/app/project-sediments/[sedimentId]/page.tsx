"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, BookOpenText, FileCheck2, RefreshCw, Save } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Button, Card, PageContainer, Toast } from "@/components/ui";
import { getProjectSediment, updateProjectSedimentNotes, type ProjectSediment } from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function ProjectSedimentDetailPage() {
  const params = useParams<{ sedimentId: string }>();
  const sedimentId = params.sedimentId;
  const [sediment, setSediment] = useState<ProjectSediment | null>(null);
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session || !sedimentId) return;
    setLoading(true);
    getProjectSediment(session.accessToken, sedimentId)
      .then((item) => {
        setSediment(item);
        setNotes(item.developerNotes);
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目沉淀加载失败"))
      .finally(() => setLoading(false));
  }, [sedimentId]);

  async function handleSaveNotes(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !sediment) return;
    setSaving(true);
    setError("");
    try {
      const updated = await updateProjectSedimentNotes(session.accessToken, sediment.id, notes);
      setSediment(updated);
      setNotice("开发者备注已保存。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "开发者备注保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <AppShell eyebrow="有来源、可确认、可复用" title={sediment?.title ?? "沉淀详情"}>
      <PageContainer>
        <Link className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-brand hover:text-brand-hover" href="/project-intelligence">
          <ArrowLeft className="h-4 w-4" />返回项目沉淀
        </Link>

        {sediment ? (
          <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
            <div className="space-y-5">
              <Card shadow="card">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge label="已确认" tone="success" />
                  <span className="text-xs text-muted">{sediment.sedimentType}</span>
                </div>
                <p className="mt-4 max-w-3xl text-base leading-7 text-slate-700 break-words">{sediment.summary}</p>
                <section className="mt-6 border-t border-line pt-5">
                  <h2 className="font-semibold text-slate-950">它解决的问题</h2>
                  <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600 break-words">{sediment.problemSolved || "当前沉淀尚未补充问题说明。"}</p>
                </section>
              </Card>

              <Card shadow="card">
                <div className="flex items-center gap-2">
                  <FileCheck2 className="h-4 w-4 text-brand" />
                  <h2 className="font-semibold text-slate-950">来源概览</h2>
                </div>
                <div className="mt-4 flex flex-wrap gap-3 text-sm text-slate-700">
                  <span className="rounded-md bg-slate-100 px-3 py-2">{sediment.sourceSegmentIds.length} 个开发推进段</span>
                  <span className="rounded-md bg-slate-100 px-3 py-2">{sediment.evidenceRefs.length} 条证据引用</span>
                  <span className="rounded-md bg-slate-100 px-3 py-2">最近更新 {new Date(sediment.updatedAt).toLocaleString()}</span>
                </div>
                <details className="mt-4 rounded-md border border-line">
                  <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50">查看证据细节</summary>
                  <ul className="max-h-72 space-y-2 overflow-auto border-t border-line p-4 font-mono text-xs text-muted">
                    {sediment.evidenceRefs.map((reference) => <li className="break-all" key={reference}>{reference}</li>)}
                  </ul>
                </details>
              </Card>
            </div>

            <aside className="space-y-5">
              <Card shadow="card">
                <div className="flex items-center gap-2">
                  <BookOpenText className="h-4 w-4 text-brand" />
                  <h2 className="font-semibold text-slate-950">可复用出口</h2>
                </div>
                <ul className="mt-3 space-y-2 text-sm leading-6 text-slate-600">
                  <li>README 项目能力说明</li>
                  <li>简历与面试项目案例</li>
                  <li>阶段复盘与每日回顾</li>
                  <li>后续 Agent 项目上下文</li>
                </ul>
              </Card>

              <Card shadow="card">
                <h2 className="font-semibold text-slate-950">开发者备注</h2>
                <p className="mt-1 text-xs leading-5 text-muted">这里是你的理解、后续计划或面试讲法，不会伪装成 ProjectFlow 自动确认的事实。</p>
                <form className="mt-4" onSubmit={handleSaveNotes}>
                  <textarea className="min-h-36 w-full resize-y rounded-md border border-line px-3 py-2 text-sm leading-6 outline-none focus:border-brand" onChange={(event) => setNotes(event.target.value)} placeholder="添加我的理解、后续计划或个人反思" value={notes} />
                  <Button className="mt-3 w-full" disabled={saving} size="sm" type="submit" variant="primary">
                    {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    {saving ? "保存中" : "保存开发者备注"}
                  </Button>
                </form>
              </Card>
            </aside>
          </div>
        ) : loading ? <div className="h-1 bg-slate-950" /> : <Card>没有找到这条项目沉淀。</Card>}
        <Toast error={error} notice={notice} />
      </PageContainer>
    </AppShell>
  );
}
