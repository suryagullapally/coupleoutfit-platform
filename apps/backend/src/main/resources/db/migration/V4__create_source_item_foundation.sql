CREATE TABLE source_item (
    id uuid PRIMARY KEY,
    owner_session_id varchar(64) NOT NULL CHECK (owner_session_id ~ '^[a-f0-9]{64}$'),
    source_type varchar(32) NOT NULL CHECK (source_type IN ('IMAGE_UPLOAD','PRODUCT_URL')),
    status varchar(32) NOT NULL CHECK (status IN ('AWAITING_UPLOAD','UPLOAD_VERIFYING','RESOLVING_URL','READY_FOR_ANALYSIS','IMAGE_UPLOAD_REQUIRED','FAILED')),
    country_code varchar(2) NOT NULL REFERENCES country(code),
    currency_code varchar(3) NOT NULL REFERENCES currency(code),
    locale_code varchar(64) NOT NULL REFERENCES locale(code),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at>=created_at),
    FOREIGN KEY (locale_code,country_code,currency_code) REFERENCES locale(code,country_code,default_currency_code),
    CHECK (source_type='PRODUCT_URL' OR status NOT IN ('RESOLVING_URL','IMAGE_UPLOAD_REQUIRED'))
);
CREATE TABLE source_asset (
    id uuid PRIMARY KEY,
    source_item_id uuid NOT NULL UNIQUE REFERENCES source_item(id),
    storage_key varchar(255) NOT NULL UNIQUE,
    media_type varchar(32) CHECK (media_type IN ('image/jpeg','image/png','image/webp')),
    byte_size bigint CHECK (byte_size>0),
    width integer CHECK (width>0),
    height integer CHECK (height>0),
    sha256 varchar(64) CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    status varchar(16) NOT NULL CHECK (status IN ('PENDING','VERIFIED','REJECTED','DELETED')),
    created_at timestamptz NOT NULL,
    verified_at timestamptz CHECK (verified_at>=created_at),
    deleted_at timestamptz CHECK (deleted_at>=created_at),
    CHECK (storage_key='source-items/' || source_item_id::text || '/' || id::text || '/original'),
    CHECK (status<>'VERIFIED' OR (media_type IS NOT NULL AND byte_size IS NOT NULL AND width IS NOT NULL AND height IS NOT NULL AND sha256 IS NOT NULL AND verified_at IS NOT NULL)),
    CHECK (status<>'DELETED' OR deleted_at IS NOT NULL)
);
ALTER TABLE product_offer ADD CONSTRAINT product_offer_id_market_key UNIQUE(id,retailer_market_id);
CREATE TABLE source_url (
    source_item_id uuid PRIMARY KEY REFERENCES source_item(id),
    original_url varchar(4096) NOT NULL,
    normalized_url varchar(4096) NOT NULL,
    resolution_status varchar(32) NOT NULL CHECK (resolution_status IN ('RESOLVED_CATALOGUE_OFFER','UNSUPPORTED')),
    resolver_type varchar(32) NOT NULL,
    resolved_retailer_market_id uuid REFERENCES retailer_market(id),
    resolved_product_offer_id uuid REFERENCES product_offer(id),
    resolved_image_url varchar(4096),
    failure_code varchar(64),
    resolved_at timestamptz NOT NULL,
    FOREIGN KEY (resolved_product_offer_id,resolved_retailer_market_id) REFERENCES product_offer(id,retailer_market_id),
    CHECK (resolution_status<>'RESOLVED_CATALOGUE_OFFER' OR (resolved_retailer_market_id IS NOT NULL AND resolved_product_offer_id IS NOT NULL AND resolved_image_url IS NOT NULL AND failure_code IS NULL))
);
