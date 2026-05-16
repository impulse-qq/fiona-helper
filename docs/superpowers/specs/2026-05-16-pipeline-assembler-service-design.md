# PipelineAssemblerService 设计文档

## 1. 概述

PipelineAssemblerService 是 prompt-forge 项目的**核心组装引擎**，负责将 Pipeline 中的 Slot 按顺序编排并组装成最终 Prompt。

本服务以 **MCP Server** 模式对外暴露 Tools，由外部 Agent 驱动组装流程。服务内部通过严格的流程控制（Pipeline 步进机制）保证 Slot 按顺序完成。

## 2. 背景与上下文

prompt-forge 是一个基于 Quarkus + PostgreSQL 的提示模板引擎。已有核心实体：

- `PipelineEntity`：包含多个有序 Slot 的管道
- `SlotEntity`：管道中的槽位，具有约束类型（FIXED/FREE/OPTIONAL）和默认值
- `ConstraintType`：FIXED（固定内容）、FREE（用户/Agent 自由填充）、OPTIONAL（可选填充）

组装流程由外部 Agent 主导：Agent 查询 Slot 信息 → 生成内容 → 插入值 → 最终组装。

## 3. 设计目标

- **流程控制**：严格的 Pipeline 顺序，前一 Slot 未完成无法进入下一步
- **请求隔离**：每次组装为独立草稿会话，数据互不干扰
- **Agent 友好**：每步返回下一步指引，缺失值时返回明确提示
- **可扩展**：支持后续多世界观、多角色等扩展

## 4. 数据模型

### 4.1 现有实体调整

#### PipelineEntity

```java
@Entity
@Table(name = "pipeline")
public class PipelineEntity extends PanacheEntityBase {
    // ... 现有字段 ...

    /** 项目级固定世界观，如 "赛博朋克" */
    @Column(name = "world_setting", length = 64)
    public String worldSetting;
}
```

#### SlotEntity

```java
@Entity
@Table(name = "pipeline_slot")
public class SlotEntity extends PanacheEntityBase {
    // ... 现有字段 ...

    /** Slot 描述/提示，帮助 Agent 理解该填什么，如 "请为角色生成详细的外貌描述" */
    @Column(name = "description", length = 512)
    public String description;

    /** 字数限制，null 表示无限制 */
    @Column(name = "word_limit")
    public Integer wordLimit;
}
```

### 4.2 新增实体

#### CharacterEntity

角色是独立实体，高于 Slot。Session 可选关联角色，Agent 通过角色获取全局上下文。

```java
@Entity
@Table(name = "story_character")
public class CharacterEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "name", nullable = false, length = 128)
    public String name;

    /** 基础设定（外貌、身份等），如 "身高180cm，短发，机械义肢左臂" */
    @Column(name = "base_design", length = 512)
    public String baseDesign;

    /** 性格/人设，如 "冷静寡言，擅长潜入" */
    @Column(name = "personality", length = 512)
    public String personality;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;
}
```

#### AssembleSessionEntity

组装会话，每个组装请求创建一条记录。Session 是 Pipeline 的实例，可选关联角色。

```java
@Entity
@Table(name = "assemble_session")
public class AssembleSessionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "pipeline_id", nullable = false)
    public UUID pipelineId;

    /** 可选关联的角色，用于获取全局上下文 */
    @Column(name = "character_id")
    public UUID characterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public SessionStatus status = SessionStatus.CREATED;

    /** 当前应该完成的 Slot 的 orderIndex */
    @Column(name = "current_slot_index", nullable = false)
    public int currentSlotIndex = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;
}
```

#### SessionStatus

```java
public enum SessionStatus {
    CREATED,      // 会话刚创建
    IN_PROGRESS,  // 正在进行中（至少已填充一个 Slot）
    COMPLETED     // 所有 Slot 已完成
}
```

#### SlotDraftEntity

存储 Agent 填充的 Slot 值，与会话隔离。

```java
@Entity
@Table(name = "slot_draft")
public class SlotDraftEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "slot_id", nullable = false)
    public UUID slotId;

    @Column(name = "value", nullable = false, length = 4000)
    public String value;

    @Column(name = "filled_at")
    public Instant filledAt;
}
```

## 5. MCP Tools 定义

### 5.1 Tool 列表

| Tool | 功能描述 |
|------|----------|
| `create_session` | 创建组装会话，返回 sessionId 和第一个 Slot 信息 |
| `get_world_setting` | 获取当前 Pipeline 的世界观设定 |
| `get_character_setting` | 获取当前 Session 关联角色的基础设计 |
| `get_slot` | 查询指定 Slot 的信息（含已完成列表、下一步指引） |
| `insert_slot_value` | 填充当前 Slot 的值（严格步进控制），**要求显式传入世界观以证明 Agent 已了解背景** |
| `assemble_prompt` | 最终组装，按 orderIndex 拼接所有草稿值为完整 Prompt |

### 5.2 create_session

**输入：**
```json
{
  "pipelineId": "uuid",
  "characterId": "uuid"
}
```

**输出：**
```json
{
  "sessionId": "uuid",
  "status": "CREATED",
  "firstSlot": {
    "id": "uuid",
    "name": "角色",
    "orderIndex": 0,
    "constraintType": "FREE",
    "defaultValue": "...",
    "wordLimit": 200
  },
  "nextStep": {
    "slotId": "uuid",
    "slotName": "角色",
    "hint": "请为角色生成详细描述"
  },
  "note": "请先调用 get_world_setting 获取世界观，调用 get_character_setting 获取当前角色基础设计"
}
```

### 5.3 get_world_setting

获取当前 Pipeline 的世界观设定。世界观是 Pipeline 级别的全局上下文，所有 Slot 共享。

**输入：**
```json
{
  "sessionId": "uuid"
}
```

**输出：**
```json
{
  "worldSetting": "赛博朋克"
}
```

### 5.4 get_character_setting

获取当前 Session 关联角色的基础设计/上下文。角色是 Session 级别的全局上下文，所有 Slot 共享。

**输入：**
```json
{
  "sessionId": "uuid"
}
```

**输出（有关联角色）：**
```json
{
  "characterId": "uuid",
  "name": "Ghost",
  "baseDesign": "身高180cm，短发，机械义肢左臂",
  "personality": "冷静寡言，擅长潜入"
}
```

**输出（无关联角色）：**
```json
{
  "characterId": null,
  "name": null,
  "baseDesign": null,
  "personality": null
}
```

### 5.5 get_slot

**输入：**
```json
{
  "sessionId": "uuid",
  "slotId": "uuid"
}
```

**输出：**
```json
{
  "slot": {
    "id": "uuid",
    "name": "outfit",
    "orderIndex": 1,
    "constraintType": "FREE",
    "defaultValue": "一套黑色战术风衣...",
    "wordLimit": 200
  },
  "progress": {
    "isCurrent": true,
    "completedCount": 1,
    "totalCount": 3,
    "currentSlotName": "outfit"
  },
  "completedSlots": [
    {
      "slotId": "uuid",
      "slotName": "角色",
      "orderIndex": 0,
      "value": "赛博朋克雇佣兵，代号Ghost..."
    }
  ],
  "nextStep": {
    "slotId": "uuid",
    "slotName": "scene",
    "hint": "设计角色所处的场景环境"
  }
}
```

### 5.6 insert_slot_value

**输入：**
```json
{
  "sessionId": "uuid",
  "slotId": "uuid",
  "value": "赛博朋克雇佣兵，代号Ghost...",
  "worldSetting": "赛博朋克"
}
```

**校验逻辑：**
- `worldSetting` 为必填字段，确保 Agent 已通过 `get_world_setting` 获取世界观信息
- 服务端做**非空校验**；本阶段不做内容一致性校验（允许 Agent 基于理解的转述）

**成功输出（中间步骤）：**
```json
{
  "success": true,
  "message": "角色 已保存",
  "sessionStatus": "IN_PROGRESS",
  "nextStep": {
    "slotId": "uuid",
    "slotName": "outfit",
    "hint": "给角色设计一套衣服"
  }
}
```

**成功输出（最后一步）：**
```json
{
  "success": true,
  "message": "outfit 已保存",
  "sessionStatus": "COMPLETED",
  "nextStep": null
}
```

**错误输出（跳步）：**
```json
{
  "success": false,
  "message": "请先完成 'outfit'（给角色设计一套衣服）"
}
```

**错误输出（重复填充）：**
```json
{
  "success": false,
  "message": "'角色' 已完成，请继续 'outfit'"
}
```

### 5.7 assemble_prompt

**输入：**
```json
{
  "sessionId": "uuid"
}
```

**成功输出：**
```json
{
  "success": true,
  "prompt": "赛博朋克雇佣兵，代号Ghost... 一套黑色战术风衣... 霓虹灯闪烁的雨夜街道..."
}
```

**错误输出（未完成）：**
```json
{
  "success": false,
  "message": "请先完成 'scene'（设计角色所处的场景环境）"
}
```

## 6. 服务层设计

### 6.1 PipelineAssemblerService

```java
@ApplicationScoped
public class PipelineAssemblerService {

    /** 创建组装会话 */
    public SessionResponse createSession(UUID pipelineId, UUID characterId);

    /** 获取世界观设定 */
    public WorldSettingResponse getWorldSetting(UUID sessionId);

    /** 获取当前 Session 关联角色的基础设计 */
    public CharacterSettingResponse getCharacterSetting(UUID sessionId);

    /** 获取 Slot 信息（含流程控制状态） */
    public SlotResponse getSlot(UUID sessionId, UUID slotId);

    /**
     * 插入 Slot 值（含流程推进）
     * worldSetting 为必填，确保 Agent 已了解背景信息
     */
    public InsertResult insertSlotValue(UUID sessionId, UUID slotId,
                                        String value, String worldSetting);

    /** 最终组装 */
    public AssembleResult assemblePrompt(UUID sessionId);
}
```

### 6.2 核心流程控制逻辑

```
insertSlotValue(sessionId, slotId, value, worldSetting):
  1. 加载 session，验证存在且状态不为 COMPLETED
  2. 如果 worldSetting 为空:
       返回 错误: "请先调用 get_world_setting 获取世界观，并在提交时显式传入"
  3. 加载 pipeline 的所有 slots，按 orderIndex 排序
  4. 确定 currentSlot = slots[session.currentSlotIndex]
  5. 如果 slotId ≠ currentSlot.id:
       返回 错误: "请先完成 '{currentSlot.name}'"
  6. 保存 SlotDraftEntity(sessionId, slotId, value, now)
  7. session.currentSlotIndex++
  8. 如果 currentSlotIndex >= slots.size():
       session.status = COMPLETED
       返回 成功: "全部完成，请调用 assemble_prompt"
  9. nextSlot = slots[session.currentSlotIndex]
       返回 成功: "{currentSlot.name} 已保存，下一步: {nextSlot.name}"
```

### 6.3 组装逻辑

```
assemblePrompt(sessionId):
  1. 加载 session，验证存在
  2. 加载 pipeline 的所有 slots，按 orderIndex 排序
  3. 如果 session.status ≠ COMPLETED:
       currentSlot = slots[session.currentSlotIndex]
       返回 错误: "请先完成 '{currentSlot.name}'"
  4. 查询所有 SlotDraftEntity，按 slot.orderIndex 排序
  5. 按顺序拼接所有 draft.value（纯文本，无模板替换）
  6. 返回 成功: 完整 prompt 字符串
```

## 7. 边界情况处理

| 场景 | 行为 |
|------|------|
| Agent 跳过当前 Slot | `insert_slot_value` 返回错误，提示当前应完成的 Slot |
| Agent 未获取世界观就提交 | `insert_slot_value` 返回错误："请先调用 get_world_setting 获取世界观" |
| Agent 查询非当前 Slot | `get_slot` 正常返回，但 `progress.isCurrent = false`，附带 `currentSlotName` |
| Agent 重复填充同一 Slot | 返回错误，提示该 Slot 已完成及下一步 |
| 组装时未完成 | `assemble_prompt` 返回错误，提示当前应完成的 Slot |
| FIXED Slot | Agent 无需填充，系统自动取 `defaultValue`。`get_slot` 仍会提示其存在，`insert` 时自动跳过 |
| sessionId 不存在 | 所有 Tool 返回统一的 "会话不存在" 错误 |
| pipelineId 不存在 | `create_session` 返回 "Pipeline 不存在" 错误 |
| characterId 不存在 | `create_session` 返回 "角色不存在" 错误（如果传了 characterId） |
| 无角色的 Session 调用 get_character_setting | 返回空值（characterId=null），Agent 可正常继续 |

## 8. Repository 层

新增 Repository：

```java
@ApplicationScoped
public class CharacterRepository implements PanacheRepository<CharacterEntity> {
    public Optional<CharacterEntity> findByIdOptional(UUID id);
}

@ApplicationScoped
public class AssembleSessionRepository implements PanacheRepository<AssembleSessionEntity> {
    public Optional<AssembleSessionEntity> findByIdOptional(UUID id);
}

@ApplicationScoped
public class SlotDraftRepository implements PanacheRepository<SlotDraftEntity> {
    public List<SlotDraftEntity> findBySessionId(UUID sessionId);
    public Optional<SlotDraftEntity> findBySessionIdAndSlotId(UUID sessionId, UUID slotId);
}
```

## 9. 扩展预留

| 扩展点 | 说明 |
|--------|------|
| 多世界观 | `create_session` 未来可接受 `worldId` 参数，`worldSetting` 从配置表读取 |
| 会话过期 | 本阶段不实现，后续可添加 `expiredAt` 字段和定时清理任务 |
| 组装历史 | 后续可添加 `PromptInstanceEntity` 记录每次组装结果，关联 character |
| 并行 Slot | 未来可支持同一 `orderIndex` 下多个 Slot 并行填充 |
| 条件分支 | 未来可根据 Slot 值动态决定后续步骤 |
| 角色-Pipeline 关联 | 后续可添加 `character_pipeline` 关联表，实现一个角色适配多个 Pipeline |

## 10. 数据库迁移

新增 Flyway 迁移脚本 `V2__add_assemble_session_and_draft.sql`：

```sql
-- 角色表（character 是 SQL 关键字，使用 story_character）
CREATE TABLE story_character (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    base_design VARCHAR(512),
    personality VARCHAR(512),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_story_character_name ON story_character(name);

-- PipelineEntity 扩展：世界观
ALTER TABLE pipeline ADD COLUMN world_setting VARCHAR(64);

-- SlotEntity 扩展：描述/提示、字数限制
ALTER TABLE pipeline_slot ADD COLUMN description VARCHAR(512);
ALTER TABLE pipeline_slot ADD COLUMN word_limit INTEGER;

-- 组装会话表
CREATE TABLE assemble_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID NOT NULL REFERENCES pipeline(id),
    character_id UUID REFERENCES story_character(id),
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    current_slot_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_assemble_session_pipeline ON assemble_session(pipeline_id);
CREATE INDEX idx_assemble_session_character ON assemble_session(character_id);

-- Slot 草稿值表
CREATE TABLE slot_draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES assemble_session(id),
    slot_id UUID NOT NULL REFERENCES pipeline_slot(id),
    value VARCHAR(4000) NOT NULL,
    filled_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_slot_draft_session ON slot_draft(session_id);
CREATE INDEX idx_slot_draft_session_slot ON slot_draft(session_id, slot_id);
```

## 11. 测试策略

- **单元测试**：`PipelineAssemblerService` 的核心流程控制逻辑（步进、跳步检测、重复填充检测）
- **集成测试**：端到端组装流程（创建会话 → 填充所有 Slot → 组装 Prompt）
- **边界测试**：跳步、重复填充、未完成组装、无效 sessionId/pipelineId/characterId、无角色会话
