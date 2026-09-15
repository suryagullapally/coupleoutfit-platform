CREATE TABLE clothing_analysis (
    id uuid PRIMARY KEY,
    source_item_id uuid NOT NULL UNIQUE REFERENCES source_item(id),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','FAILED')),
    current_revision_id uuid,
    failure_code varchar(100),
    created_at timestamptz NOT NULL,
    started_at timestamptz CHECK (started_at>=created_at),
    completed_at timestamptz CHECK (completed_at>=started_at),
    updated_at timestamptz NOT NULL CHECK (updated_at>=created_at),
    CHECK ((status='COMPLETED')=(current_revision_id IS NOT NULL)),
    CHECK (status<>'COMPLETED' OR completed_at IS NOT NULL),
    CHECK (status='PENDING' OR started_at IS NOT NULL),
    CHECK ((status='FAILED')=(failure_code IS NOT NULL)),
    CHECK (failure_code IS NULL OR failure_code ~ '^ANALYSIS_[A-Z_]{1,80}$')
);
CREATE TABLE analysis_revision (
    id uuid PRIMARY KEY,
    clothing_analysis_id uuid NOT NULL REFERENCES clothing_analysis(id),
    revision_number integer NOT NULL CHECK (revision_number>0),
    revision_type varchar(24) NOT NULL CHECK (revision_type IN ('AI_GENERATED','USER_CORRECTED')),
    parent_revision_id uuid,
    attributes jsonb NOT NULL CHECK (jsonb_typeof(attributes)='object'),
    confidence jsonb CHECK (jsonb_typeof(confidence)='object'),
    schema_version varchar(16) NOT NULL CHECK (schema_version='1'),
    provider varchar(32),
    model varchar(120),
    provider_request_id varchar(200) CHECK (provider_request_id ~ '^[A-Za-z0-9_-]{1,200}$'),
    prompt_version varchar(100),
    created_at timestamptz NOT NULL,
    UNIQUE(clothing_analysis_id,revision_number),
    UNIQUE(id,clothing_analysis_id),
    FOREIGN KEY (parent_revision_id,clothing_analysis_id) REFERENCES analysis_revision(id,clothing_analysis_id),
    CHECK ((revision_type='AI_GENERATED' AND revision_number=1 AND parent_revision_id IS NULL AND provider IS NOT NULL AND provider='OPENAI' AND model IS NOT NULL AND btrim(model)<>'' AND prompt_version IS NOT NULL AND btrim(prompt_version)<>'')
        OR (revision_type='USER_CORRECTED' AND revision_number>1 AND parent_revision_id IS NOT NULL AND provider IS NULL AND model IS NULL AND provider_request_id IS NULL AND prompt_version IS NULL AND confidence IS NULL))
);
ALTER TABLE clothing_analysis ADD CONSTRAINT clothing_analysis_current_revision_fk
    FOREIGN KEY (current_revision_id,id) REFERENCES analysis_revision(id,clothing_analysis_id);

-- Revision contents are append-only even for accidental direct SQL updates.
CREATE FUNCTION prevent_analysis_revision_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Analysis revisions are immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER analysis_revision_immutable BEFORE UPDATE ON analysis_revision
    FOR EACH ROW EXECUTE FUNCTION prevent_analysis_revision_update();
