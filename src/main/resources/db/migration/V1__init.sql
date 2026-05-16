-- Pipeline definition
CREATE TABLE pipeline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    description TEXT,
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_pipeline_name ON pipeline(name);

-- Pipeline slot (ordered steps)
CREATE TABLE pipeline_slot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID NOT NULL REFERENCES pipeline(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    order_index INT NOT NULL,
    constraint_type VARCHAR(16) NOT NULL CHECK (constraint_type IN ('FIXED', 'FREE', 'OPTIONAL')),
    default_value TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_slot_pipeline_id ON pipeline_slot(pipeline_id);
CREATE INDEX idx_slot_order ON pipeline_slot(pipeline_id, order_index);
