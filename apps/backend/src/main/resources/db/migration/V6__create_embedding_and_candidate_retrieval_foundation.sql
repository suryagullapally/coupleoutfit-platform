-- Exact cosine retrieval initially: no speculative ANN indexes. In-flight claims have no vector.
CREATE TABLE product_embedding (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES product(id),
    embedding_type varchar(32) NOT NULL CHECK (embedding_type='STYLE_COMPATIBILITY'),
    provider varchar(32) NOT NULL CHECK (provider ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    model varchar(120) NOT NULL CHECK (model ~ '^[A-Za-z0-9._:-]{1,120}$'),
    dimensions integer NOT NULL CHECK (dimensions=1536),
    descriptor_version varchar(80) NOT NULL CHECK (descriptor_version ~ '^[a-z0-9-]{1,80}$'),
    source_version varchar(160) NOT NULL CHECK (btrim(source_version)<>''),
    descriptor_hash varchar(64) NOT NULL CHECK (descriptor_hash ~ '^[a-f0-9]{64}$'),
    source_updated_at timestamptz NOT NULL,
    embedding vector(1536) CHECK (vector_norm(embedding)>0),
    provider_request_id varchar(200) CHECK (provider_request_id ~ '^[A-Za-z0-9_-]{1,200}$'),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at>=created_at),
    completed_at timestamptz CHECK (completed_at>=created_at),
    CHECK ((embedding IS NULL)=(completed_at IS NULL)),
    UNIQUE (product_id,embedding_type,provider,model,descriptor_version,source_version)
);

CREATE TABLE analysis_embedding (
    id uuid PRIMARY KEY,
    analysis_revision_id uuid NOT NULL REFERENCES analysis_revision(id),
    embedding_type varchar(32) NOT NULL CHECK (embedding_type='STYLE_COMPATIBILITY'),
    provider varchar(32) NOT NULL CHECK (provider ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    model varchar(120) NOT NULL CHECK (model ~ '^[A-Za-z0-9._:-]{1,120}$'),
    dimensions integer NOT NULL CHECK (dimensions=1536),
    descriptor_version varchar(80) NOT NULL CHECK (descriptor_version ~ '^[a-z0-9-]{1,80}$'),
    source_version varchar(160) NOT NULL CHECK (btrim(source_version)<>''),
    descriptor_hash varchar(64) NOT NULL CHECK (descriptor_hash ~ '^[a-f0-9]{64}$'),
    source_updated_at timestamptz NOT NULL,
    embedding vector(1536) CHECK (vector_norm(embedding)>0),
    provider_request_id varchar(200) CHECK (provider_request_id ~ '^[A-Za-z0-9_-]{1,200}$'),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at>=created_at),
    completed_at timestamptz CHECK (completed_at>=created_at),
    CHECK ((embedding IS NULL)=(completed_at IS NULL)),
    UNIQUE (analysis_revision_id,embedding_type,provider,model,descriptor_version,source_version)
);
