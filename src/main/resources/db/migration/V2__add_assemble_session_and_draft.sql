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
