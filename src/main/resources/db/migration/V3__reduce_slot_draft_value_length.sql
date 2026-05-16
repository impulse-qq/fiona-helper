-- 缩减 slot_draft.value 长度上限从 4000 到 500
ALTER TABLE slot_draft ALTER COLUMN value TYPE VARCHAR(500);

-- 为 assemble_session 添加乐观锁版本号列
ALTER TABLE assemble_session ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
