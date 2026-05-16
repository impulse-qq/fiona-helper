-- M2: 引入 slot_prompt 表(可复用 prompt 内容池)
-- 替代 slot_draft 表 + pipeline_slot.default_value 字段
-- session_id=NULL 表示预设(来自老 default_value 或人工维护)
-- session_id 非空表示某次 session 产生的实际填充值
-- character_id 预留 M3 list_slot_prompts 时按角色筛选用

CREATE TABLE slot_prompt (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id      UUID NOT NULL REFERENCES pipeline_slot(id) ON DELETE CASCADE,
    character_id UUID REFERENCES story_character(id),
    session_id   UUID REFERENCES assemble_session(id),
    content      TEXT NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    created_by   VARCHAR(64)
);

CREATE INDEX idx_slot_prompt_slot ON slot_prompt(slot_id);
CREATE INDEX idx_slot_prompt_session ON slot_prompt(session_id);
CREATE INDEX idx_slot_prompt_slot_character ON slot_prompt(slot_id, character_id);

-- 数据迁移:把现有非空 default_value 转成 session_id=NULL 的预设记录
INSERT INTO slot_prompt (slot_id, session_id, character_id, content, created_by)
SELECT id, NULL, NULL, default_value, 'system-migration'
FROM pipeline_slot
WHERE default_value IS NOT NULL;

-- 删除老的 slot_draft 表(被 slot_prompt 取代)
DROP TABLE IF EXISTS slot_draft;

-- 删除 pipeline_slot.default_value 列(被 slot_prompt session_id=NULL 记录取代)
ALTER TABLE pipeline_slot DROP COLUMN default_value;
