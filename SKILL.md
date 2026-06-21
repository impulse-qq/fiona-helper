---
name: fiona-helper
description: Use when an agent needs to assemble prompts through the Fiona Helper MCP pipeline engine, inspect pipeline slots, manage prompt assembly sessions, submit session scores, or associate generated images with completed sessions.
---

# Fiona Helper — 提示模板引擎 Skill

> 将 fiona-helper 作为 Skill 提供给外部 Agent，用于 Pipeline 驱动的提示词（Prompt）组装与管理。

## 项目概述

基于 Quarkus + MCP Server 的提示模板引擎。支持：

- **Pipeline 组装**：按预定义 Slot 顺序步进填充，最终组装成完整 Prompt
- **角色管理**：为组装会话绑定角色，注入角色设定到 Prompt
- **评分系统**：对已完成会话进行 1-5 分评分
- **图片归档**：将 AI 生成的图片关联到已完成会话
- **Web UI**：Vue 3 + Vite 前端，管理角色，浏览 Pipeline/Session，查看组装内容并上传 Session 图片

服务地址（默认）：`http://localhost:8080`
前端地址（默认）：`http://localhost:5173`

## 核心概念

| 概念 | 说明 |
|------|------|
| **Pipeline** | 提示词组装模板。由多个有序 Slot 组成，带有世界观设定 (`worldSetting`) |
| **Slot** | Pipeline 中的单个步骤。含 `name`、`orderIndex`、`constraintType`（FIXED/FREE/OPTIONAL）、`description`、`wordLimit` |
| **Character** | 角色。含 `name`、`baseDesign`（基础设计）、`personality`（性格标签） |
| **AssembleSession** | 一次 Pipeline 组装实例。状态流转：CREATED → IN_PROGRESS → COMPLETED |
| **SlotPrompt** | Slot 内容池。`session_id=NULL` 为预设内容；非空为某次 session 的沉淀 |
| **SessionScore** | 会话评分。1-5 分，按 `created_by` 去重（覆盖更新） |

## MCP Tools（主要接口）

MCP Server 通过 HTTP SSE 暴露，Agent 可直接调用以下 Tools：

### 组装流程

```
create_session(pipelineId, characterId) → {sessionId, status, firstSlot, nextStep}
  创建组装会话。返回 sessionId 和第一个 Slot 信息。
  - pipelineId: Pipeline UUID（必填）
  - characterId: 角色 UUID（可选，传 null 则无角色）

get_world_setting(sessionId) → {worldSetting}
  获取 Pipeline 的世界观设定，作为全局上下文。

get_character_setting(sessionId) → {characterId, name, baseDesign, personality}
  获取会话关联角色的基础设计/人设，作为角色全局上下文。

get_slot(sessionId, slotId) → {slot, progress, completedSlots, nextStep}
  查询 Slot 信息，包含当前进度、已完成 Slot 列表和下一步指引。

insert_slot_value(sessionId, slotId, value, worldSetting) → {success, message, sessionStatus, nextStep}
  填充当前 Slot 的值。必须显式传入 worldSetting 以证明已了解背景。
  服务端严格按 orderIndex 顺序控制，禁止跳步/回退。
  返回 nextStep 告知下一个 Slot（或 null 表示全部完成）。

assemble_prompt(sessionId) → {success, prompt, message}
  最终组装：按顺序拼接所有已填充的 Slot 值为完整 Prompt。
  只有所有 Slot 完成后才能调用。
```

### Pipeline 查询

```
list_pipelines(limit, offset) → [PipelineSummary...]
  列出可用 Pipeline。limit 默认 50，最大 200。
  返回字段：id, name, description, worldSetting

get_pipeline(pipelineId) → {id, name, description, worldSetting, slots[]}
  获取 Pipeline 详情，包含所有 Slot 的有序定义。
  Slot 字段：id, name, orderIndex, constraintType, description, wordLimit
```

### 评分

```
submit_score(sessionId, overallScore, comment, createdBy) → {success, message, scoreId, isUpdate}
  对已完成 session 提交评分。overallScore 必须在 1-5 范围。
  同一 createdBy 重提交会覆盖原评分。

get_score(sessionId) → {sessionId, scores[], avgScore, scoreCount}
  查询某 session 的所有评分、平均分和评分人数。
```

### 图片登记

```
register_session_image(sessionId, filename) → String (image URL)
  通过 MCP 将图片文件名登记到已完成的 session。
  注意：该 Tool 只记录文件名，不负责上传文件；实际文件需通过外部流程或 REST API 上传。
  - session 必须处于 COMPLETED 状态
  - filename 不能为空
  - filename 会被服务端清洗为安全文件名
```

## 标准组装流程

Agent 使用 Pipeline 组装 Prompt 的标准流程：

```
1. list_pipelines() → 选择 pipeline，记录 pipelineId
2. get_pipeline(pipelineId) → 查看 Slot 定义、description、wordLimit
3. create_session(pipelineId, characterId) → 获取 sessionId + firstSlot
4. get_world_setting(sessionId) → 了解世界观（后续步骤必须传入）
5. get_character_setting(sessionId) → 了解角色设定（如有角色）
6. 对每个 Slot 循环：
   a. get_slot(sessionId, slotId) → 了解当前 Slot 要求
   b. 生成 Slot 内容值 value
   c. insert_slot_value(sessionId, slotId, value, worldSetting)
      → 成功则继续下一个 Slot
      → nextStep 为 null 时全部完成
7. assemble_prompt(sessionId) → 获取完整 Prompt
8. （可选）submit_score(sessionId, score, comment, createdBy) → 提交评分
9. （可选）register_session_image(sessionId, filename) → 登记生成图片文件名
```

**重要约束**：
- `insert_slot_value` 必须按 orderIndex 顺序填充，禁止跳过
- `insert_slot_value` 必须传入正确的 `worldSetting`，否则服务端会拒绝
- `insert_slot_value.value` 最大长度为 500 字符
- FIXED Slot 会从 `slot_prompt` 预设中自动填充
- `assemble_prompt` 只能在所有 Slot 填充完成后调用
- `register_session_image` 只能对已完成的 session 调用，且仅登记文件名

## REST API

除 MCP Tools 外，同时暴露 REST API 供前端/Web 调用：

### Characters

```
GET    /api/characters              → 角色列表
POST   /api/characters              → 创建角色 {name, baseDesign, personality}
GET    /api/characters/{id}         → 角色详情
PUT    /api/characters/{id}         → 更新角色 {name, baseDesign, personality}
DELETE /api/characters/{id}         → 删除角色
POST   /api/characters/{id}/avatar  → 上传头像 (multipart/form-data, file 字段)
```

### Pipelines

```
GET /api/pipelines              → Pipeline 列表
GET /api/pipelines/{id}         → Pipeline 详情（含 slots）
GET /api/pipelines/{id}/sessions → 该 Pipeline 的已完成会话列表
```

### Sessions

```
GET  /api/sessions/{id}           → Session 详情（含 slots + images）
GET  /api/sessions/{id}/images    → Session 图片列表
POST /api/sessions/{id}/images    → 上传图片 (multipart/form-data, file 字段)
  - session 必须处于 COMPLETED 状态
  - 只支持图片文件（contentType 以 image/ 开头）
  - 文件大小不能超过 5MB
```

### Session Images（直接访问）

```
GET /api/session-images/{sessionId}/{filename} → 获取图片文件
```

## 数据库结构参考

| 表名 | 关键字段 |
|------|---------|
| `pipeline` | id, name, description, world_setting, is_public |
| `pipeline_slot` | id, pipeline_id, name, order_index, constraint_type, description, word_limit |
| `story_character` | id, name, base_design, personality, avatar_path |
| `assemble_session` | id, pipeline_id, character_id, status, current_slot_index |
| `slot_prompt` | id, slot_id, character_id, session_id, content, created_by |
| `session_score` | id, session_id, overall_score, comment, created_by |
| `session_image` | id, session_id, image_path |

## 启动方式

```bash
# 后端（热重载）
./mvnw quarkus:dev

# 前端（需要后端已启动）
cd src/main/frontend && npm run dev

# 完整打包
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

## 依赖

- Java 17+ / GraalVM
- PostgreSQL 14+
- Maven 3.9+
- Node.js 18+（前端开发）
