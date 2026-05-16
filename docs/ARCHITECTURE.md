# 架构文档

## 概述

fiona-helper 是一个基于 **Quarkus** 的提示模板引擎，以 **MCP Server** 模式对外提供服务。核心能力是将预定义的 Pipeline（管道）中的 Slot（槽位）按顺序组装为完整的 Prompt。

## 技术栈

| 组件 | 技术 |
|------|------|
| 运行时 | Quarkus 3.35.3 |
| 数据库 | PostgreSQL + Flyway |
| ORM | Hibernate ORM with Panache |
| API | MCP Server (HTTP transport) |
| 编译 | GraalVM Native Image (推荐) |

## 系统架构

```
┌─────────────────┐
│   MCP Client    │  ← 外部 Agent
│  (Claude/Cursor)│
└────────┬────────┘
         │ HTTP (MCP Protocol)
         ▼
┌─────────────────────────┐
│   MCP Server Endpoint   │  ← quarkus-mcp-server-http
│   (PipelineAssemblerTools)│
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  PipelineAssemblerService│ ← 核心组装引擎
└────────┬────────────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌──────────┐
│Entity  │ │Repository│
│Layer   │ │Layer     │
└────────┘ └──────────┘
         │
         ▼
┌─────────────────────────┐
│      PostgreSQL         │
└─────────────────────────┘
```

## 核心实体

### PipelineEntity
Pipeline 是最高层级，包含一组有序的 Slot。

### SlotEntity
Slot 是 Pipeline 中的槽位，有三种约束类型：
- **FIXED**: 固定内容，Agent 无需填充，自动取 `defaultValue`
- **FREE**: 自由填充，Agent 必须提供内容
- **OPTIONAL**: 可选填充，本阶段未实现

### CharacterEntity
角色是独立实体，Session 可选关联角色。角色提供全局上下文（基础设计、性格），影响所有 Slot 的生成。

### AssembleSessionEntity
每次组装创建一个 Session，记录当前进度（`currentSlotIndex`）和状态（`CREATED`/`IN_PROGRESS`/`COMPLETED`）。

### SlotPromptEntity
统一的 Slot Prompt 内容池(M2 引入,替代 M1 的 `SlotDraftEntity` 和 `pipeline_slot.default_value`):
- `session_id IS NULL`：预设/手维护内容(原 M1 `default_value` 迁移而来,供 FIXED slot 自动跳过时填充)
- `session_id` 非空：某次 session 通过 `insert_slot_value` 沉淀的实际填充值
- `character_id` 字段在 M2 引入,预留按角色筛选预设/可复用 prompt 的能力

## 流程控制

组装流程由外部 Agent 主导，服务层通过 `currentSlotIndex` 严格限制顺序：

```
1. Agent 调用 create_session → 创建 Session，返回第一个 FREE Slot
2. Agent 调用 get_world_setting → 获取世界观
3. Agent 调用 get_character_setting → 获取角色设定（可选）
4. Agent 调用 insert_slot_value → 填充当前 Slot（需传入世界观证明）
   └─ 服务端验证 slotId == currentSlot，保存后 currentSlotIndex++
5. 重复步骤 4 直到所有 Slot 完成
6. Agent 调用 assemble_prompt → 按顺序拼接所有 draft.value
```

## 数据流

```
Agent
  │ create_session(pipelineId, characterId?)
  │
  ▼
AssembleSessionEntity [CREATED, currentSlotIndex=0]
  │
  │ (自动跳过 FIXED slots)
  ▼
Agent 获取世界观 + 角色设定
  │
  │ insert_slot_value(sessionId, slotId, value, worldSetting)
  ▼
SlotPromptEntity (sessionId, slotId, characterId, content)
  │
  ▼
AssembleSessionEntity [IN_PROGRESS, currentSlotIndex++]
  │
  ▼ (所有 Slot 完成后)
AssembleSessionEntity [COMPLETED]
  │
  │ assemble_prompt(sessionId)
  ▼
完整 Prompt 字符串
```

## 安全与隔离

- **请求隔离**: 每次组装为独立 Session，数据互不干扰
- **步进控制**: 严格的 Pipeline 顺序，前一 Slot 未完成无法进入下一步
- **世界观验证**: `insert_slot_value` 要求显式传入世界观，确保 Agent 已了解背景
- **内容校验**: 本阶段不做内容一致性校验（允许 Agent 基于理解的转述）
