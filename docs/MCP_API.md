# MCP API 文档

## 概述

fiona-helper 以 **MCP Server** 模式运行，通过 HTTP 暴露 6 个 Tools 供外部 Agent 调用。

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
