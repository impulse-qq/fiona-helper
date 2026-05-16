# MCP API 文档

## 概述

fiona-helper 以 **MCP Server** 模式运行，通过 HTTP 暴露 10 个 Tools 供外部 Agent 调用。

## Tools

### create_session

创建新的 Pipeline 组装会话。

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

### get_world_setting

获取当前 Pipeline 的世界观设定。

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

### get_character_setting

获取当前 Session 关联角色的基础设计。

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

### get_slot

查询指定 Slot 的信息。

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

### insert_slot_value

填充当前 Slot 的值。

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
- `worldSetting` 为必填字段
- 服务端做非空校验，不做内容一致性校验
- 严格的步进控制：只能填充当前 Slot

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

### assemble_prompt

最终组装完整 Prompt。

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

## 标准错误

| 场景 | 错误信息 |
|------|----------|
| sessionId 不存在 | `会话不存在` |
| pipelineId 不存在 | `Pipeline 不存在` |
| characterId 不存在 | `角色不存在` |
| 未获取世界观 | `请先调用 get_world_setting 获取世界观，并在提交时显式传入` |

## 组装流程示例

```
1. create_session(pipelineId="xxx")
   → { sessionId: "sess-1", firstSlot: { name: "角色", ... } }

2. get_world_setting(sessionId="sess-1")
   → { worldSetting: "赛博朋克" }

3. get_character_setting(sessionId="sess-1")
   → { characterId: "char-1", name: "Ghost", ... }

4. insert_slot_value(sessionId="sess-1", slotId="slot-1", value="...", worldSetting="赛博朋克")
   → { success: true, nextStep: { slotName: "outfit" } }

5. insert_slot_value(sessionId="sess-1", slotId="slot-2", value="...", worldSetting="赛博朋克")
   → { success: true, nextStep: { slotName: "scene" } }

6. insert_slot_value(sessionId="sess-1", slotId="slot-3", value="...", worldSetting="赛博朋克")
   → { success: true, sessionStatus: "COMPLETED" }

7. assemble_prompt(sessionId="sess-1")
   → { success: true, prompt: "赛博朋克雇佣兵... 黑色风衣... 雨夜街道..." }
```

## 评分工具(M3a)

### submit_score

对某次已完成的 session 提交评分(1-5)。同一 `createdBy` 重提交会覆盖原评分(按 `(sessionId, createdBy)` 去重)。

**输入：**
```json
{
  "sessionId": "uuid",
  "overallScore": 5,
  "comment": "结构清晰、世界观贴合",
  "createdBy": "user-A"
}
```

**校验逻辑：**
- `sessionId` 对应 session 必须存在且 `status == COMPLETED`
- `overallScore` 必填,1-5 整数(0 / 6 / null 拒绝)
- `createdBy` 必填,非空非空白(纯空白如 `"   "` 拒绝)
- `comment` 可选,无长度上限(DB `TEXT`)
- 业务规则错误一律软失败(`success=false`)

**成功输出(首次评分)：**
```json
{
  "success": true,
  "message": "评分已提交",
  "scoreId": "uuid",
  "isUpdate": false
}
```

**成功输出(同 createdBy 覆盖)：**
```json
{
  "success": true,
  "message": "评分已更新",
  "scoreId": "uuid",
  "isUpdate": true
}
```

**错误输出(session 未完成)：**
```json
{
  "success": false,
  "message": "session 未完成,当前状态: IN_PROGRESS,需先完成所有 slot 后再评分",
  "scoreId": null,
  "isUpdate": false
}
```

**错误输出(分数越界)：**
```json
{
  "success": false,
  "message": "overallScore 必须在 1-5 范围(当前: 6)",
  "scoreId": null,
  "isUpdate": false
}
```

### get_score

查询某 session 的所有评分(每个 `createdBy` 一条记录)+ 平均分 + 评分人数。

**输入：**
```json
{
  "sessionId": "uuid"
}
```

**成功输出(有评分)：**
```json
{
  "sessionId": "uuid",
  "scores": [
    {
      "id": "uuid",
      "overallScore": 5,
      "comment": "结构清晰",
      "createdBy": "user-A",
      "createdAt": "2026-05-16T23:45:00Z",
      "updatedAt": "2026-05-16T23:45:00Z"
    },
    {
      "id": "uuid",
      "overallScore": 4,
      "comment": null,
      "createdBy": "user-B",
      "createdAt": "2026-05-16T23:50:00Z",
      "updatedAt": "2026-05-16T23:50:00Z"
    }
  ],
  "avgScore": 4.5,
  "scoreCount": 2
}
```

**成功输出(无评分)：**
```json
{
  "sessionId": "uuid",
  "scores": [],
  "avgScore": null,
  "scoreCount": 0
}
```

**错误输出(session 不存在,抛 RuntimeException → MCP 协议错误)：**
- session 不存在时方法抛 `IllegalArgumentException`,经工具层转为 `RuntimeException`,客户端看到 MCP 协议层错误响应

## 已知限制(M3a)

- **无认证**:`createdBy` 由 Agent / 调用方自报,M3a 不做身份验证。生产部署前应统一引入 API Key
- **并发竞态**:同 `(sessionId, createdBy)` 并发提交可能触发 DB UNIQUE 约束违反 → 500。M3a 接受该风险(MCP 调用通常串行)
- **createdBy 长度**:超过 64 字符走 DB 字段限制,会抛 SQLException → 500,不在 service 层校验
- **comment 长度**:不限制(DB `TEXT`),已知 abuse 风险
