from __future__ import annotations

import copy
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import urllib.parse
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest import mock
from pathlib import Path

from projectflow_obsidian import (
    AtomicWriter,
    BEGIN_MARKER,
    END_MARKER,
    GatewayClient,
    MANIFEST_NAME,
    ObsidianProjection,
    ProjectionError,
    event_month,
    filename,
    obsidian_advanced_uri,
    obsidian_open_uri,
)


PROJECT_ID = "11111111-1111-1111-1111-111111111111"
CAP_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
CAP_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
FACT_JUNE = "10000000-0000-0000-0000-000000000001"
FACT_JULY = "10000000-0000-0000-0000-000000000002"
CHAPTER_A = "chapter-11111111111111111111"
CHAPTER_B = "chapter-22222222222222222222"
STORY_A = "story-11111111111111111111"
STORY_B = "story-22222222222222222222"
STORY_C = "story-33333333333333333333"
THREAD_A = "thread-11111111111111111111"


def fact(fact_id: str, title: str, occurred: str, recorded: str, capabilities: list[str] | None = None, status: str = "RECORDED") -> dict:
    return {
        "factId": fact_id,
        "title": title,
        "summary": f"{title} 的可追溯项目事实。",
        "time": {"occurredFrom": occurred, "occurredTo": occurred, "eventAt": occurred, "recordedAt": recorded, "analyzedAt": recorded, "syncedAt": None},
        "batchId": "90000000-0000-0000-0000-000000000001",
        "commitCount": 1,
        "fileCount": 2,
        "agentResultCount": 1,
        "evidenceCount": 3,
        "recordStatus": status,
        "qualityStatus": "PASS",
        "attentionReason": "" if status == "RECORDED" else "证据边界需要关注",
        "relatedCapabilityIds": capabilities or [],
        "truthLayer": "SOURCE",
    }


def capability(capability_id: str, name: str, version: int = 2) -> dict:
    return {
        "capabilityId": capability_id,
        "canonicalName": name,
        "aliases": [name + "旧称"],
        "summary": f"{name} 已成为项目的长期能力。",
        "problemSolved": "解决长期项目理解与追溯问题。",
        "longTermValue": "让外部消费者共享稳定项目语义。",
        "productAreas": ["project-memory"],
        "status": "ACTIVE",
        "maturity": "CONTINUOUSLY_ENHANCED",
        "maturityReason": "由多个事实、提交和演进持续证明。",
        "firstFormedAt": "2026-06-15T10:00:00Z",
        "lastEnhancedAt": "2026-07-17T09:00:00Z",
        "factCount": 2,
        "batchCount": 2,
        "commitCount": 2,
        "evidenceCount": 5,
        "evolutionCount": 2,
        "attentionCount": 0,
        "currentVersion": version,
        "mergedIntoCapabilityId": None,
        "stale": False,
        "sourceUpdatedAt": "2026-07-17T09:00:00Z",
        "readmeExpression": f"{name} 提供稳定、可追溯的项目能力。",
        "resumeExpression": "",
        "interviewExpression": "",
        "truthLayer": "DERIVED",
    }


def evolution(evolution_id: str, capability_id: str, fact_id: str, period: str, version: int) -> dict:
    return {
        "evolutionId": evolution_id,
        "capabilityId": capability_id,
        "capabilityName": "项目记忆",
        "type": "NEW_CAPABILITY" if version == 1 else "ENHANCE_CAPABILITY",
        "versionBefore": version - 1,
        "versionAfter": version,
        "title": "形成能力" if version == 1 else "增强能力",
        "summary": "事实证据推动能力形成或增强。",
        "occurredAt": f"{period}-17T09:00:00Z",
        "sourceFactCount": 1,
        "sourceBatchCount": 1,
        "sourcePeriods": [period],
        "sourceFactIds": [fact_id],
        "mergedFromCapabilityId": None,
        "truthLayer": "DERIVED",
    }


def month(period: str, facts: list[dict], status: str = "READY") -> dict:
    return {
        "periodKey": period,
        "periodStart": f"{period}-01T00:00:00Z",
        "periodEnd": f"{period}-28T23:59:59Z",
        "stats": {"factCount": len(facts), "batchCount": len(facts), "commitCount": len(facts), "fileCount": len(facts) * 2, "agentResultCount": len(facts), "attentionCount": sum(item["recordStatus"] != "RECORDED" for item in facts)},
        "summary": {"status": status, "summary": f"{period} 完成了真实项目能力演进。", "sourceFactCount": len(facts), "coveredFactCount": len(facts), "stale": False, "generationVersion": 1, "generatedAt": f"{period}-28T23:59:59Z", "notice": ""},
        "themes": [{"themeId": str(uuid.uuid5(uuid.NAMESPACE_URL, period)), "title": "项目记忆演进", "summary": "事实、时间线和能力协同演进。", "factCount": len(facts)}],
        "sourceFactCount": len(facts),
        "coveredFactCount": len(facts),
        "facts": {"items": facts, "totalElements": len(facts), "hasMore": False},
        "history": {"status": "COMPLETED", "coveredCommitCount": len(facts), "totalCommitCount": len(facts), "remainingCommitCount": 0, "notice": ""},
    }


def dataset() -> dict:
    june_fact = fact(FACT_JUNE, "建立项目事实记忆", "2026-06-15T10:00:00Z", "2026-06-15T11:00:00Z", [CAP_A])
    july_fact = fact(FACT_JULY, "按发生时间维护项目历程", "2026-07-17T09:00:00Z", "2026-08-20T09:00:00Z", [CAP_A, CAP_B])
    months = [month("2026-06", [june_fact]), month("2026-07", [july_fact])]
    capabilities = [capability(CAP_A, "项目事实记忆"), capability(CAP_B, "统一外部读取")]
    evolutions = {
        CAP_A: [
            evolution("20000000-0000-0000-0000-000000000001", CAP_A, FACT_JUNE, "2026-06", 1),
            evolution("20000000-0000-0000-0000-000000000002", CAP_A, FACT_JULY, "2026-07", 2),
        ],
        CAP_B: [evolution("20000000-0000-0000-0000-000000000003", CAP_B, FACT_JULY, "2026-07", 1)],
    }
    stories = [
        {
            "id": STORY_A, "primarySubjectKey": "project-memory", "humanTitle": "新增项目事实记忆并形成初始结果",
            "oneSentenceSummary": "项目开始保存可追溯的事实记忆。", "beforeState": "此前没有可确认的事实记忆。",
            "change": "来源记录显示新增了项目事实记忆。", "afterState": "项目事实记忆已经存在。",
            "affectedAreas": ["项目记忆"], "reason": "", "reasonEvidenceRefs": [],
            "laterOutcome": "随后继续扩展为按时间组织的项目历程。", "conflicts": [], "unknowns": ["原因保持 UNKNOWN。"],
            "occurredFrom": "2026-06-15T10:00:00Z", "occurredTo": "2026-06-15T10:00:00Z",
            "evidenceCount": 2, "rawEventCount": 2, "authority": "ENGINEERING_GROUPING", "summaryStatus": "DETERMINISTIC",
            "coverage": "FULL_WITHIN_DISCOVERED_SOURCES", "limitations": [],
            "eventRefs": ["30000000-0000-0000-0000-000000000001"], "evidenceRefs": [f"fact:{FACT_JUNE}", "commit:aaaa"],
        },
        {
            "id": STORY_B, "primarySubjectKey": "project-memory", "humanTitle": "调整项目事实记忆并按发生时间组织历程",
            "oneSentenceSummary": "事实记忆增加了真实发生时间维度。", "beforeState": "事实已存在但时间阅读层有限。",
            "change": "来源记录显示按发生时间组织项目事实。", "afterState": "项目历程可以按真实发生时间阅读。",
            "affectedAreas": ["项目记忆", "时间线"], "reason": "", "reasonEvidenceRefs": [],
            "laterOutcome": "随后形成统一外部读取。", "conflicts": [], "unknowns": ["原因保持 UNKNOWN。"],
            "occurredFrom": "2026-07-17T09:00:00Z", "occurredTo": "2026-07-17T09:00:00Z",
            "evidenceCount": 2, "rawEventCount": 2, "authority": "ENGINEERING_GROUPING", "summaryStatus": "DETERMINISTIC",
            "coverage": "FULL_WITHIN_DISCOVERED_SOURCES", "limitations": [],
            "eventRefs": ["30000000-0000-0000-0000-000000000002"], "evidenceRefs": [f"fact:{FACT_JULY}", "commit:bbbb"],
        },
        {
            "id": STORY_C, "primarySubjectKey": "project-memory", "humanTitle": "恢复统一读取入口并重新连接项目记忆",
            "oneSentenceSummary": "被移除的统一读取入口重新出现。", "beforeState": "统一读取入口此前已被移除。",
            "change": "来源记录显示统一读取入口被恢复。", "afterState": "统一读取入口重新存在。",
            "affectedAreas": ["外部读取"], "reason": "", "reasonEvidenceRefs": [], "laterOutcome": "",
            "conflicts": [], "unknowns": ["恢复原因保持 UNKNOWN。"],
            "occurredFrom": "2026-07-20T09:00:00Z", "occurredTo": "2026-07-20T09:00:00Z",
            "evidenceCount": 2, "rawEventCount": 2, "authority": "ENGINEERING_GROUPING", "summaryStatus": "DETERMINISTIC",
            "coverage": "FULL_WITHIN_DISCOVERED_SOURCES", "limitations": [],
            "eventRefs": ["30000000-0000-0000-0000-000000000003"], "evidenceRefs": ["commit:cccc", "file:integrations/hermes/projectflow_mcp.py"],
        },
    ]
    chapters = [
        {
            "id": CHAPTER_A, "title": "2026-06：项目事实记忆形成", "summary": "事实记忆形成并可追溯。",
            "from": "2026-06-15T10:00:00Z", "to": "2026-06-15T10:00:00Z", "boundarySignals": ["EARLIEST_DISCOVERED_EVENT"],
            "storyRefs": [STORY_A], "storyCount": 1, "rawEventCount": 2, "authority": "ENGINEERING_GROUPING",
            "coverage": "FULL_WITHIN_DISCOVERED_SOURCES", "limitations": [],
        },
        {
            "id": CHAPTER_B, "title": "2026-07：历程与统一读取扩展", "summary": "项目历程和统一读取在同一时间区间继续演变。",
            "from": "2026-07-17T09:00:00Z", "to": "2026-07-20T09:00:00Z", "boundarySignals": ["TIME_GAP_32_DAYS"],
            "storyRefs": [STORY_B, STORY_C], "storyCount": 2, "rawEventCount": 4, "authority": "ENGINEERING_GROUPING",
            "coverage": "FULL_WITHIN_DISCOVERED_SOURCES", "limitations": [],
        },
    ]
    threads = [{
        "id": THREAD_A, "subjectKey": "project-memory", "subjectLabel": "项目记忆", "subjectType": "PROJECT_SUBJECT",
        "storyRefs": [STORY_A, STORY_B, STORY_C], "transitions": ["CREATED", "MODIFIED", "REMOVED", "RESTORED"],
        "currentOutcome": "统一读取入口已经恢复。", "gaps": [], "conflicts": [], "unknowns": ["恢复原因保持 UNKNOWN。"],
        "evidenceCount": 6, "capabilityId": None,
    }]
    history_overview = {
        "projectId": PROJECT_ID, "status": "READY", "projectRevision": "git:history", "sourceEventCount": 6,
        "earliestEventAt": "2026-06-15T10:00:00Z", "latestEventAt": "2026-07-20T09:00:00Z",
        "strategyVersion": "project-history-v1", "promptVersion": "project-history-synthesis-v1",
        "overview": {
            "earliestConfirmedState": "最早可确认项目形成了事实记忆。", "currentState": "最近可确认统一读取入口已经恢复。",
            "chapters": chapters, "recentChanges": ["恢复统一读取入口并重新连接项目记忆（2026-07-20）"],
            "conflicts": [], "unknowns": ["恢复原因保持 UNKNOWN。"],
        },
        "coverage": {"complete": True, "currentness": "CURRENT", "discoveredEventCount": 6, "currentEventCount": 6,
                     "staleEventCount": 0, "invalidatedEventCount": 0, "sourceCounts": {"GIT": 4, "PROJECT_FACT": 2},
                     "gaps": [], "limitations": []},
        "diagnostics": {}, "analysisJobId": None, "generatedAt": "2026-08-20T10:00:00Z",
        "latestSuccessfulAt": "2026-08-20T10:00:00Z", "updatedAt": "2026-08-20T10:00:00Z",
        "errorCode": "", "errorSummary": "",
    }
    return {
        "snapshot": {
            "project": {"projectId": PROJECT_ID, "name": "ProjectFlow", "summary": "自动维护项目从创建至今的长期记忆。", "status": "BUILDING", "techStack": ["Java", "Python"]},
            "branch": "master", "factCount": 2, "recordedFactCount": 2, "attentionFactCount": 0,
            "coveredCommitCount": 2, "totalCommitCount": 2, "earliestFactAt": "2026-06-15T10:00:00Z", "latestFactAt": "2026-07-17T09:00:00Z",
            "recentChanges": {"items": [july_fact, june_fact]}, "activeCapabilityCount": 2,
            "lifecycleSummary": {"status": "READY", "summary": "项目从事实记忆演进为统一外部语义层。", "notice": ""},
            "projectHistory": history_overview,
            "health": {"historyStatus": "COMPLETED", "projectHistoryStatus": "READY", "timelineStatus": "READY", "capabilityMapStatus": "READY", "capabilityMapStale": False, "latestRealChangeAt": "2026-07-17T09:00:00Z", "warnings": []},
        },
        "historyOverview": history_overview,
        "historyChapters": chapters,
        "historyStories": stories,
        "historyThreads": threads,
        "lifecycle": {"timelineZone": "Asia/Shanghai", "lifecycle": {"summary": {"status": "READY", "summary": "项目从事实记忆演进为统一外部语义层。", "notice": ""}, "history": {"status": "COMPLETED"}}},
        "months": months,
        "capabilities": capabilities,
        "evolutions": evolutions,
    }


class FakeSource:
    def __init__(self, value: dict | None = None):
        self.value = value or dataset()
        self.calls = 0
        self.app_url = "http://127.0.0.1:3000"

    def collect(self, project_id: str) -> dict:
        self.calls += 1
        if project_id != PROJECT_ID:
            raise ProjectionError("PROJECT_NOT_FOUND", "项目不存在")
        return copy.deepcopy(self.value)


class ObsidianProjectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.vault = Path(self.temp.name) / "vault"
        self.vault.mkdir()
        self.source = FakeSource()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def projection(self, **kwargs) -> ObsidianProjection:
        return ObsidianProjection(self.source, self.vault, now=lambda: "2026-08-20T12:00:00Z", **kwargs)

    def managed(self) -> Path:
        return self.vault / "ProjectFlow"

    def test_first_sync_creates_complete_core_with_metadata_links_and_no_fact_files(self) -> None:
        result = self.projection().sync(PROJECT_ID)
        self.assertEqual("COMPLETED", result["status"])
        self.assertEqual(17, result["plan"]["CREATED"])
        expected = [
            "项目概览.md", "项目历程/索引.md", "项目历程/2026-06.md", "项目历程/2026-07.md", "项目事实/2026-06.md", "项目事实/2026-07.md",
            "索引/能力索引.md", "索引/时间索引.md", "索引/事实索引.md",
        ]
        for relative in expected:
            self.assertTrue((self.managed() / relative).is_file(), relative)
        capability_files = list((self.managed() / "项目能力").glob("*.md"))
        self.assertEqual(2, len(capability_files))
        self.assertEqual(2, len(list((self.managed() / "项目历程/篇章").glob("*.md"))))
        self.assertEqual(3, len(list((self.managed() / "项目历程/变化故事").glob("*.md"))))
        self.assertEqual(1, len(list((self.managed() / "项目历程/演变链").glob("*.md"))))
        self.assertFalse((self.managed() / "重要事实").exists())
        overview = (self.managed() / "项目概览.md").read_text(encoding="utf-8")
        for key in ["projectflow_managed: true", "projectflow_project_id:", "entity_type:", "entity_id:", "source_version:", "content_hash:", "generated_at:", "source_updated_at:", "projection_version:"]:
            self.assertIn(key, overview)
        self.assertIn(BEGIN_MARKER, overview)
        self.assertIn(END_MARKER, overview)
        self.assertIn("[[项目能力/", overview)
        self.assertIn("[[项目历程/", overview)
        self.assertIn("obsidian_open_uri: \"obsidian://open?", overview)
        self.assertIn(f"http://127.0.0.1:3000/projects/{PROJECT_ID}/history", overview)
        self.assertIn("# 我的笔记", overview)
        manifest = json.loads((self.managed() / MANIFEST_NAME).read_text(encoding="utf-8"))
        self.assertEqual(PROJECT_ID, manifest["projectId"])
        self.assertEqual(17, len(manifest["files"]))
        self.assertTrue(result["deepLinks"]["overview"]["officialFallback"].startswith("obsidian://open?"))
        self.assertTrue(result["integrationLevels"]["level0MarkdownAndOfficialUri"])
        self.assertFalse(result["integrationLevels"]["level2LocalRestOrMcp"])

    def test_noop_has_zero_writes_and_stable_hashes(self) -> None:
        first = self.projection().sync(PROJECT_ID)
        hashes = {path.relative_to(self.managed()).as_posix(): path.read_bytes() for path in self.managed().rglob("*.md")}
        manifest_before = (self.managed() / MANIFEST_NAME).read_bytes()
        second = self.projection().sync(PROJECT_ID)
        self.assertEqual(17, second["plan"]["UNCHANGED"])
        self.assertEqual(0, second["noteWrites"])
        self.assertEqual(0, second["totalWrites"])
        self.assertEqual(manifest_before, (self.managed() / MANIFEST_NAME).read_bytes())
        self.assertEqual(hashes, {path.relative_to(self.managed()).as_posix(): path.read_bytes() for path in self.managed().rglob("*.md")})
        self.assertGreater(first["bytesWritten"], 0)

    def test_history_notes_remain_readable_without_plugins_and_advanced_uri_degrades_to_official(self) -> None:
        first = self.projection().sync(PROJECT_ID)
        self.assertFalse(first["integrationLevels"]["level1AdvancedUri"])
        self.assertEqual(
            first["deepLinks"]["history"]["officialFallback"],
            first["deepLinks"]["history"]["preferred"],
        )
        story_note = next((self.managed() / "项目历程/变化故事").glob("*.md"))
        rendered = story_note.read_text(encoding="utf-8")
        self.assertIn("## 原来状态", rendered)
        self.assertIn("## 本次变化", rendered)
        self.assertIn("## 当前结果", rendered)
        self.assertIn("展示来源：自动整理 · 阅读位置：主要变化", rendered)
        self.assertNotIn("展示权威：AUTOMATIC", rendered)
        self.assertNotIn("角色：PRIMARY", rendered)
        self.assertNotIn("ENGINEERING_GROUPING", rendered)
        self.assertNotIn("DETERMINISTIC", rendered)
        self.assertIn("ProjectFlow：", rendered)
        self.assertIn(f"http://127.0.0.1:3000/projects/{PROJECT_ID}/history?type=story", rendered)

        plugin_root = self.vault / ".obsidian/plugins/obsidian-advanced-uri"
        plugin_root.mkdir(parents=True)
        (plugin_root / "manifest.json").write_text('{"id":"obsidian-advanced-uri"}', encoding="utf-8")
        (self.vault / ".obsidian/community-plugins.json").write_text('["obsidian-advanced-uri"]', encoding="utf-8")
        upgraded = self.projection().sync(PROJECT_ID)
        self.assertTrue(upgraded["integrationLevels"]["level1AdvancedUri"])
        self.assertTrue(upgraded["deepLinks"]["history"]["preferred"].startswith("obsidian://advanced-uri?"))
        self.assertTrue(upgraded["deepLinks"]["history"]["officialFallback"].startswith("obsidian://open?"))
        self.assertGreater(upgraded["plan"]["UPDATED"], 0)

        official = obsidian_open_uri("知识 Vault", "ProjectFlow/项目历程/索引.md")
        advanced = obsidian_advanced_uri("知识 Vault", "ProjectFlow/项目历程/变化故事/示例.md", "Before")
        self.assertEqual("obsidian", urllib.parse.urlparse(official).scheme)
        self.assertEqual("obsidian", urllib.parse.urlparse(advanced).scheme)
        self.assertIn("heading=Before", advanced)

    def test_corrected_split_role_conflict_and_merge_projection_stay_consistent(self) -> None:
        data = copy.deepcopy(dataset())
        stories = data["historyStories"]
        primary = stories[0]
        supporting = stories[1]
        conflicted = stories[2]
        split = copy.deepcopy(primary)
        split.update({
            "id": "story-44444444444444444444",
            "humanTitle": "拆分报告附录并形成独立可读版本",
            "oneSentenceSummary": "报告附录已经从主体内容中拆出并可单独阅读。",
            "eventRefs": ["30000000-0000-0000-0000-000000000004"],
            "evidenceRefs": ["document:appendix"],
            "evidenceCount": 1,
            "rawEventCount": 1,
            "occurredFrom": "2026-06-16T10:00:00Z",
            "occurredTo": "2026-06-16T10:00:00Z",
        })
        primary["supportingChangeRefs"] = [supporting["id"]]
        supporting.update({
            "role": "SUPPORTING",
            "primaryStoryId": primary["id"],
            "presentationAuthority": "USER_DECLARED_PRESENTATION",
            "pinned": True,
        })
        conflicted["correctionConflicts"] = ["来源成员已经变化，旧展示修正未应用。"]
        conflicted["displayStatus"] = "CONFLICT"
        stories.append(split)
        first_chapter = data["historyChapters"][0]
        first_chapter["storyRefs"] = [primary["id"], split["id"]]
        first_chapter["storyCount"] = 2
        first_chapter["rawEventCount"] = 3
        data["historyThreads"][0]["storyRefs"] = [primary["id"], split["id"], supporting["id"], conflicted["id"]]
        data["historyCorrections"] = {
            "items": [
                {"id": "correction-role", "status": "ACTIVE", "difference": "将该变化归为支撑工作。"},
                {"id": "correction-merge", "status": "CONFLICT", "difference": "待合并对象已被新来源替换。"},
            ],
            "presentationRevision": "presentation:corrected",
            "total": 2,
        }
        self.source = FakeSource(data)

        result = self.projection().sync(PROJECT_ID)
        self.assertEqual("COMPLETED", result["status"])
        notes = list((self.managed() / "项目历程/变化故事").glob("*.md"))
        self.assertEqual(4, len(notes))
        rendered = {path.name: path.read_text(encoding="utf-8") for path in notes}
        supporting_note = next(value for value in rendered.values() if supporting["humanTitle"] in value)
        conflict_note = next(value for value in rendered.values() if conflicted["humanTitle"] in value)
        split_note = next(value for value in rendered.values() if split["humanTitle"] in value)
        self.assertIn("展示来源：经过用户修改 · 阅读位置：支撑工作", supporting_note)
        self.assertNotIn("USER_DECLARED_PRESENTATION", supporting_note)
        self.assertNotIn("SUPPORTING", supporting_note)
        self.assertIn("展示修正冲突：来源成员已经变化，旧展示修正未应用。", conflict_note)
        self.assertIn("document:appendix", split_note)

        chapter_note = next((self.managed() / "项目历程/篇章").glob("*.md")).read_text(encoding="utf-8")
        thread_note = next((self.managed() / "项目历程/演变链").glob("*.md")).read_text(encoding="utf-8")
        self.assertIn(split["humanTitle"], chapter_note)
        self.assertIn(split["humanTitle"], thread_note)
        overview = (self.managed() / "项目概览.md").read_text(encoding="utf-8")
        self.assertIn("展示修正：2 条", overview)
        self.assertIn("待合并对象已被新来源替换", overview)

    def test_projectflow_backlinks_use_stable_frontend_routes_in_a_real_temporary_vault(self) -> None:
        self.projection().sync(PROJECT_ID)
        story_note = next((self.managed() / "项目历程/变化故事").glob("*.md"))
        rendered = story_note.read_text(encoding="utf-8")
        match = re.search(r"https?://[^)\s\"']+/projects/[^)\s\"']+/history\?type=story&amp;id=[^)\s\"']+", rendered)
        if match is None:
            match = re.search(r"https?://[^)\s\"']+/projects/[^)\s\"']+/history\?type=story&id=[^)\s\"']+", rendered)
        self.assertIsNotNone(match)
        parsed = urllib.parse.urlparse(match.group(0).replace("&amp;", "&"))
        self.assertEqual("127.0.0.1", parsed.hostname)
        self.assertEqual(f"/projects/{PROJECT_ID}/history", parsed.path)
        query = urllib.parse.parse_qs(parsed.query)
        self.assertEqual(["story"], query["type"])
        self.assertIn(query["id"][0], {STORY_A, STORY_B, STORY_C})

    def test_projectflow_app_backlinks_reject_remote_or_credentialed_urls(self) -> None:
        for value in ["https://example.com", "http://user@127.0.0.1:3000", "http://127.0.0.1:3000?token=secret"]:
            self.source.app_url = value
            with self.assertRaisesRegex(ProjectionError, "local loopback"):
                self.projection()

    def test_incremental_july_fact_updates_only_affected_notes_and_preserves_june(self) -> None:
        self.projection().sync(PROJECT_ID)
        before = {path.relative_to(self.managed()).as_posix(): path.stat().st_mtime_ns for path in self.managed().rglob("*.md")}
        new_fact = fact("10000000-0000-0000-0000-000000000003", "补充七月事实", "2026-07-20T09:00:00Z", "2026-08-21T09:00:00Z", [CAP_A])
        self.source.value["months"][1] = month("2026-07", [self.source.value["months"][1]["facts"]["items"][0], new_fact])
        self.source.value["snapshot"]["factCount"] = 3
        self.source.value["snapshot"]["recordedFactCount"] = 3
        self.source.value["snapshot"]["latestFactAt"] = "2026-07-20T09:00:00Z"
        time.sleep(0.01)
        result = self.projection().sync(PROJECT_ID)
        self.assertGreaterEqual(result["plan"]["UPDATED"], 3)
        after = {path.relative_to(self.managed()).as_posix(): path.stat().st_mtime_ns for path in self.managed().rglob("*.md")}
        self.assertEqual(before["项目历程/2026-06.md"], after["项目历程/2026-06.md"])
        self.assertEqual(before["项目事实/2026-06.md"], after["项目事实/2026-06.md"])
        self.assertGreater(after["项目历程/2026-07.md"], before["项目历程/2026-07.md"])
        self.assertGreater(after["项目事实/2026-07.md"], before["项目事实/2026-07.md"])

    def test_late_analysis_fact_is_only_in_july_projection(self) -> None:
        self.projection().sync(PROJECT_ID)
        july = (self.managed() / "项目事实/2026-07.md").read_text(encoding="utf-8")
        self.assertIn(FACT_JULY, july)
        self.assertIn("2026-07-17", july)
        self.assertFalse((self.managed() / "项目事实/2026-08.md").exists())
        timeline = (self.managed() / "项目历程/2026-07.md").read_text(encoding="utf-8")
        self.assertIn("按发生时间维护项目历程", timeline)

    def test_user_content_and_unknown_frontmatter_are_preserved(self) -> None:
        self.projection().sync(PROJECT_ID)
        path = self.managed() / "项目概览.md"
        text = path.read_text(encoding="utf-8")
        text = text.replace("---\n" + BEGIN_MARKER, "custom_tag: 用户标签\n---\n" + BEGIN_MARKER, 1)
        text += "\n用户永久笔记：不要覆盖。\n"
        path.write_text(text, encoding="utf-8")
        self.source.value["snapshot"]["project"]["summary"] = "更新后的项目定位。"
        result = self.projection().sync(PROJECT_ID)
        self.assertEqual(1, result["plan"]["UPDATED"])
        updated = path.read_text(encoding="utf-8")
        self.assertIn("custom_tag: 用户标签", updated)
        self.assertIn("用户永久笔记：不要覆盖。", updated)
        self.assertIn("更新后的项目定位。", updated)

    def test_managed_block_edit_creates_conflict_without_overwrite(self) -> None:
        self.projection().sync(PROJECT_ID)
        path = self.managed() / "项目概览.md"
        original = path.read_text(encoding="utf-8")
        changed = original.replace("## 项目历程", "## 用户修改了自动区域")
        path.write_text(changed, encoding="utf-8")
        result = self.projection().sync(PROJECT_ID)
        self.assertEqual("COMPLETED_WITH_CONFLICTS", result["status"])
        self.assertEqual(1, result["plan"]["CONFLICT"])
        self.assertEqual(changed, path.read_text(encoding="utf-8"))
        conflicts = json.loads((self.managed() / ".projectflow-conflicts.json").read_text(encoding="utf-8"))
        self.assertEqual("MANAGED_BLOCK_EDITED", conflicts["conflicts"][0]["reason"])
        path.write_text(original, encoding="utf-8")
        resolved = self.projection().sync(PROJECT_ID)
        self.assertEqual(0, resolved["plan"]["CONFLICT"])
        manifest = json.loads((self.managed() / MANIFEST_NAME).read_text(encoding="utf-8"))
        self.assertEqual([], manifest["conflicts"])
        cleared = json.loads((self.managed() / ".projectflow-conflicts.json").read_text(encoding="utf-8"))
        self.assertEqual([], cleared["conflicts"])

    def test_capability_rename_keeps_stable_path_and_merge_creates_redirect(self) -> None:
        self.projection().sync(PROJECT_ID)
        manifest = json.loads((self.managed() / MANIFEST_NAME).read_text(encoding="utf-8"))
        key = f"CAPABILITY:{CAP_A}"
        old_path = manifest["files"][key]["path"]
        self.source.value["capabilities"][0]["canonicalName"] = "重命名后的项目记忆"
        self.source.value["capabilities"][0]["summary"] = "名称变化但稳定身份不变。"
        renamed = self.projection().sync(PROJECT_ID)
        self.assertGreaterEqual(renamed["plan"]["UPDATED"], 1)
        manifest = json.loads((self.managed() / MANIFEST_NAME).read_text(encoding="utf-8"))
        self.assertEqual(old_path, manifest["files"][key]["path"])
        self.assertIn("重命名后的项目记忆", (self.managed() / old_path).read_text(encoding="utf-8"))

        self.source.value["capabilities"][0]["status"] = "MERGED"
        self.source.value["capabilities"][0]["mergedIntoCapabilityId"] = CAP_B
        merged = self.projection().sync(PROJECT_ID)
        self.assertEqual(1, merged["plan"]["REDIRECTED"])
        old_note = (self.managed() / old_path).read_text(encoding="utf-8")
        self.assertIn("已合并到", old_note)
        self.assertIn(CAP_B, old_note)
        manifest = json.loads((self.managed() / MANIFEST_NAME).read_text(encoding="utf-8"))
        self.assertEqual(CAP_B, manifest["redirects"][key])

    def test_user_move_is_discovered_and_reconciled_with_updated_open_uri(self) -> None:
        self.projection().sync(PROJECT_ID)
        manifest_path = self.managed() / MANIFEST_NAME
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        key = f"CAPABILITY:{CAP_A}"
        original = self.managed() / manifest["files"][key]["path"]
        moved = self.managed() / "我的分类/项目事实记忆.md"
        moved.parent.mkdir()
        original.rename(moved)
        moved_before = moved.read_text(encoding="utf-8")
        result = self.projection().sync(PROJECT_ID)
        self.assertTrue(moved.exists())
        moved_after = moved.read_text(encoding="utf-8")
        self.assertNotEqual(moved_before, moved_after)
        self.assertIn("obsidian_open_uri: \"obsidian://open?", moved_after)
        self.assertIn("%E6%88%91%E7%9A%84%E5%88%86%E7%B1%BB", moved_after)
        moved_plan = next(item for item in result["items"] if item["key"] == key)
        self.assertEqual("UPDATED", moved_plan["action"])
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual("我的分类/项目事实记忆.md", manifest["files"][key]["path"])

    def test_filename_safety_handles_unicode_reserved_names_and_case_collisions(self) -> None:
        collision_a = "aaaaaaaa-0000-0000-0000-000000000001"
        collision_b = "aaaaaaaa-0000-0000-0000-000000000002"
        self.source.value["capabilities"] = [capability(collision_a, "ＣＯＮ"), capability(collision_b, "con")]
        self.source.value["evolutions"] = {collision_a: [], collision_b: []}
        self.projection().sync(PROJECT_ID)
        names = [path.name for path in (self.managed() / "项目能力").glob("*.md")]
        self.assertEqual(2, len(names))
        self.assertEqual(2, len({name.casefold() for name in names}))
        self.assertTrue(all("--" in name for name in names))
        self.assertTrue(all(not any(char in name for char in '<>:"/\\|?*') for name in names))
        self.assertEqual("_CON", filename("ＣＯＮ"))

    def test_traversal_and_symlink_escape_are_rejected(self) -> None:
        with self.assertRaisesRegex(ProjectionError, "safe relative"):
            ObsidianProjection(self.source, self.vault, "../outside")
        outside = Path(self.temp.name) / "outside"
        outside.mkdir()
        link = self.vault / "linked"
        if os.name == "nt":
            created = subprocess.run(
                ["cmd.exe", "/d", "/c", "mklink", "/J", str(link), str(outside)],
                check=False, capture_output=True, text=True,
            )
            if created.returncode != 0:
                self.skipTest("junction creation is unavailable")
        else:
            try:
                os.symlink(outside, link, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("symlink creation is unavailable")
        with self.assertRaisesRegex(ProjectionError, "symlink"):
            ObsidianProjection(self.source, self.vault, "linked/project")

    def test_atomic_writer_reports_disk_error_and_removes_partial_temp(self) -> None:
        target = self.vault / "managed" / "note.md"
        writer = AtomicWriter()
        with mock.patch("os.replace", side_effect=OSError(28, "disk full")):
            with self.assertRaisesRegex(ProjectionError, "Atomic managed-file write failed"):
                writer.write(target, "complete content")
        self.assertFalse(target.exists())
        self.assertEqual([], list(target.parent.glob("*.tmp")))

    def test_interrupted_sync_leaves_only_complete_note_and_restart_recovers(self) -> None:
        interrupted = self.projection(interrupt_after_notes=1)
        with self.assertRaisesRegex(ProjectionError, "Injected interruption"):
            interrupted.sync(PROJECT_ID)
        notes = list(self.managed().rglob("*.md"))
        self.assertEqual(1, len(notes))
        content = notes[0].read_text(encoding="utf-8")
        self.assertIn(BEGIN_MARKER, content)
        self.assertIn(END_MARKER, content)
        self.assertFalse((self.managed() / MANIFEST_NAME).exists())
        recovered = self.projection().sync(PROJECT_ID)
        self.assertEqual("COMPLETED", recovered["status"])
        self.assertTrue((self.managed() / MANIFEST_NAME).is_file())
        self.assertEqual(17, len(list(self.managed().rglob("*.md"))))

    def test_corrupt_manifest_is_rebuilt_without_rewriting_notes(self) -> None:
        self.projection().sync(PROJECT_ID)
        manifest = self.managed() / MANIFEST_NAME
        before = {path.relative_to(self.managed()).as_posix(): path.read_bytes() for path in self.managed().rglob("*.md")}
        manifest.write_text("{broken", encoding="utf-8")
        result = self.projection().sync(PROJECT_ID)
        self.assertTrue(result["manifestRecovered"])
        self.assertEqual(0, result["noteWrites"])
        self.assertEqual(before, {path.relative_to(self.managed()).as_posix(): path.read_bytes() for path in self.managed().rglob("*.md")})
        rebuilt = json.loads(manifest.read_text(encoding="utf-8"))
        self.assertEqual(17, len(rebuilt["files"]))

    def test_dry_run_status_validate_and_profiles_do_not_call_a_model(self) -> None:
        projection = self.projection()
        validation = projection.validate(PROJECT_ID)
        self.assertEqual("READY", validation["status"])
        dry = projection.dry_run(PROJECT_ID)
        self.assertFalse(dry["executed"])
        self.assertFalse(self.managed().exists())
        self.assertEqual(17, dry["plan"]["CREATED"])
        status = projection.status(PROJECT_ID)
        self.assertFalse(status["executed"])
        self.assertFalse(self.managed().exists())
        extended = self.projection(profile="EXTENDED").dry_run(PROJECT_ID)
        full = self.projection(profile="FULL_FACTS").dry_run(PROJECT_ID)
        self.assertGreater(extended["plan"]["CREATED"], dry["plan"]["CREATED"])
        self.assertEqual(dry["plan"]["CREATED"] + 2, full["plan"]["CREATED"])
        self.assertEqual(5, self.source.calls)

    def test_profile_downgrade_archives_fact_notes_once_without_deletion(self) -> None:
        self.projection(profile="FULL_FACTS").sync(PROJECT_ID)
        fact_notes = list((self.managed() / "重要事实").rglob("*.md"))
        self.assertEqual(2, len(fact_notes))
        archived = self.projection(profile="CORE").sync(PROJECT_ID)
        self.assertEqual(2, archived["plan"]["ARCHIVED"])
        self.assertTrue(all(path.exists() for path in fact_notes))
        noop = self.projection(profile="CORE").sync(PROJECT_ID)
        self.assertEqual(0, noop["totalWrites"])
        manifest = json.loads((self.managed() / MANIFEST_NAME).read_text(encoding="utf-8"))
        self.assertEqual(2, sum(1 for item in manifest["files"].values() if item["status"] == "ARCHIVED"))

    def test_missing_vault_and_remote_gateway_are_rejected(self) -> None:
        with self.assertRaisesRegex(ProjectionError, "does not exist"):
            ObsidianProjection(self.source, self.vault / "missing")
        with self.assertRaisesRegex(ProjectionError, "local loopback"):
            GatewayClient("https://example.com")

    def test_correction_pages_preserve_all_entries_and_one_revision(self) -> None:
        client = GatewayClient("http://127.0.0.1:18080")
        corrections = [
            {"id": f"correction-{index:03d}", "status": "ACTIVE"}
            for index in range(125)
        ]

        def response(_path: str, params: dict | None = None) -> dict:
            page = int((params or {}).get("page", 0))
            size = int((params or {}).get("size", 50))
            start = page * size
            return {
                "items": corrections[start:start + size],
                "presentationRevision": "presentation:stable",
                "page": page,
                "size": size,
                "total": len(corrections),
            }

        with mock.patch.object(client, "get", side_effect=response) as get:
            result = client._correction_pages("/history/corrections")

        self.assertEqual(corrections, result["items"])
        self.assertEqual("presentation:stable", result["presentationRevision"])
        self.assertEqual(125, result["total"])
        self.assertEqual(2, get.call_count)

    def test_cli_uses_only_local_gateway_and_reaches_noop(self) -> None:
        class Handler(BaseHTTPRequestHandler):
            data: dict = {}
            caller_headers: list[str] = []

            def do_GET(self) -> None:  # noqa: N802 - stdlib handler contract
                Handler.caller_headers.append(self.headers.get("X-ProjectFlow-Caller", ""))
                parsed = urllib.parse.urlparse(self.path)
                query = urllib.parse.parse_qs(parsed.query)
                base = f"/api/projects/{PROJECT_ID}/project-memory"
                response: dict | None = None
                if parsed.path == base + "/snapshot":
                    response = Handler.data["snapshot"]
                elif parsed.path == base + "/history/overview":
                    response = Handler.data["historyOverview"]
                elif parsed.path == base + "/history/chapters":
                    items = Handler.data["historyChapters"]
                    response = {"items": items, "page": 0, "size": 100, "totalElements": len(items), "totalPages": 1}
                elif parsed.path == base + "/history/stories":
                    items = Handler.data["historyStories"]
                    response = {"items": items, "page": 0, "size": 100, "totalElements": len(items), "totalPages": 1}
                elif parsed.path == base + "/history/threads":
                    items = Handler.data["historyThreads"]
                    response = {"items": items, "page": 0, "size": 100, "totalElements": len(items), "totalPages": 1}
                elif parsed.path == base + "/timeline" and query.get("granularity") == ["LIFECYCLE"]:
                    response = Handler.data["lifecycle"]
                elif parsed.path == base + "/timeline" and query.get("granularity") == ["MONTH"]:
                    period = query.get("periodKey", [""])[0]
                    if period:
                        selected = next(item for item in Handler.data["months"] if item["periodKey"] == period)
                        response = {"period": selected}
                    else:
                        items = [{key: value for key, value in item.items() if key != "facts"} for item in Handler.data["months"]]
                        response = {"periods": {"items": items, "hasMore": False, "totalElements": len(items)}}
                elif parsed.path == base + "/capabilities":
                    response = {"items": Handler.data["capabilities"], "hasMore": False, "totalElements": len(Handler.data["capabilities"])}
                elif parsed.path.startswith(base + "/capabilities/") and parsed.path.endswith("/evolution"):
                    capability_id = parsed.path[len(base + "/capabilities/"):-len("/evolution")]
                    items = Handler.data["evolutions"].get(capability_id, [])
                    response = {"items": items, "hasMore": False, "totalElements": len(items)}
                if response is None:
                    self.send_response(404)
                    payload = {"error": {"code": "NOT_FOUND", "message": "not found"}}
                else:
                    self.send_response(200)
                    payload = {"data": response}
                raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

            def log_message(self, _format: str, *args: object) -> None:
                return

        Handler.data = dataset()
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        script = Path(__file__).with_name("projectflow_obsidian.py")

        def invoke(command: str) -> dict:
            command_args = [
                sys.executable, str(script), command, "--vault", str(self.vault), "--project-id", PROJECT_ID,
                "--base-url", f"http://127.0.0.1:{server.server_port}",
            ]
            completed = subprocess.run(
                command_args, check=False, capture_output=True, text=True, encoding="utf-8",
                env={**os.environ, "PYTHONUTF8": "1"}, timeout=20,
            )
            self.assertEqual(0, completed.returncode, completed.stderr)
            return json.loads(completed.stdout)

        try:
            dry = invoke("dry-run")
            self.assertFalse(dry["executed"])
            self.assertFalse(self.managed().exists())
            first = invoke("sync")
            self.assertEqual(17, first["noteWrites"])
            second = invoke("sync")
            self.assertEqual(0, second["totalWrites"])
            self.assertTrue(Handler.caller_headers)
            self.assertEqual({"obsidian-projection"}, set(Handler.caller_headers))
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_large_core_projection_is_bounded_and_measured(self) -> None:
        large = dataset()
        months: list[dict] = []
        all_facts: list[dict] = []
        for month_index in range(36):
            year = 2024 + month_index // 12
            number = month_index % 12 + 1
            period = f"{year:04d}-{number:02d}"
            facts = []
            for fact_index in range(139 if month_index < 32 else 138):
                number_id = month_index * 139 + fact_index + 1
                fact_id = str(uuid.UUID(int=number_id))
                cap_id = str(uuid.UUID(int=10_000 + number_id % 100))
                facts.append(fact(fact_id, f"规模事实 {number_id}", f"{period}-15T10:00:00Z", f"{period}-20T10:00:00Z", [cap_id]))
            months.append(month(period, facts))
            all_facts.extend(facts)
        self.assertEqual(5000, len(all_facts))
        caps = [capability(str(uuid.UUID(int=10_000 + index)), f"规模能力 {index}", 10) for index in range(100)]
        evolutions: dict[str, list[dict]] = {}
        for index, cap in enumerate(caps):
            cap_id = cap["capabilityId"]
            values = []
            for version in range(1, 11):
                source_fact = all_facts[(index * 10 + version) % len(all_facts)]
                values.append(evolution(str(uuid.UUID(int=50_000 + index * 10 + version)), cap_id, source_fact["factId"], event_month(source_fact), version))
            evolutions[cap_id] = values
        large["months"] = months
        large["capabilities"] = caps
        large["evolutions"] = evolutions
        large["snapshot"]["factCount"] = 5000
        large["snapshot"]["recordedFactCount"] = 5000
        large["snapshot"]["activeCapabilityCount"] = 100
        source = FakeSource(large)
        started = time.perf_counter()
        result = ObsidianProjection(source, self.vault, now=lambda: "2026-08-20T12:00:00Z").sync(PROJECT_ID)
        elapsed_ms = (time.perf_counter() - started) * 1000
        markdown_files = len(list(self.managed().rglob("*.md")))
        self.assertEqual(183, markdown_files)
        self.assertLess(markdown_files, 250)
        self.assertEqual(0, result["plan"]["CONFLICT"])
        self.assertEqual(1, source.calls)
        noop = ObsidianProjection(source, self.vault, now=lambda: "2026-08-20T12:00:00Z").sync(PROJECT_ID)
        self.assertEqual(0, noop["totalWrites"])
        print(f"OBSIDIAN_METRIC facts=5000 months=36 capabilities=100 evolutions=1000 files={markdown_files} first_sync_ms={elapsed_ms:.1f} writes={result['totalWrites']} bytes={result['bytesWritten']} noop_writes={noop['totalWrites']}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
