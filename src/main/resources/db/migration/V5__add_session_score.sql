-- M3a: session 粒度的评分反馈表
-- 关联 assemble_session,按 (session_id, created_by) 唯一去重,重提交=覆盖
-- 不引入 overall_score 单独索引(M3a 范围内无低分查询 MCP 工具)

CREATE TABLE session_score (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID NOT NULL REFERENCES assemble_session(id) ON DELETE CASCADE,
    overall_score INT  NOT NULL CHECK (overall_score BETWEEN 1 AND 5),
    comment       TEXT,
    created_by    VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (session_id, created_by)
);

CREATE INDEX idx_session_score_session ON session_score(session_id);
