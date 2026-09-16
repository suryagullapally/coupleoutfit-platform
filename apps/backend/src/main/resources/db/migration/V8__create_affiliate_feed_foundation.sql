CREATE TABLE affiliate_program (
    id uuid PRIMARY KEY,
    retailer_market_id uuid NOT NULL REFERENCES retailer_market(id),
    network varchar(32) NOT NULL CHECK (network='GENERIC'),
    external_program_id varchar(200) CHECK (btrim(external_program_id)<>''),
    name varchar(160) NOT NULL CHECK (btrim(name)<>''),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','PAUSED','DISABLED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at>=created_at),
    UNIQUE(retailer_market_id,network,external_program_id),
    UNIQUE(id,retailer_market_id)
);
CREATE TABLE affiliate_feed (
    id uuid PRIMARY KEY,
    affiliate_program_id uuid NOT NULL,
    retailer_market_id uuid NOT NULL REFERENCES retailer_market(id),
    external_feed_id varchar(200) CHECK (btrim(external_feed_id)<>''),
    name varchar(160) NOT NULL CHECK (btrim(name)<>''),
    format varchar(16) NOT NULL CHECK (format='CSV'),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','PAUSED','DISABLED')),
    source_type varchar(32) NOT NULL CHECK (source_type='LOCAL_FILE'),
    source_location varchar(255) NOT NULL CHECK (source_location ~ '^[A-Za-z0-9_/-]+\.csv$' AND source_location !~ '^/' AND source_location !~ '(^|/)\.\.(/|$)'),
    mapping_config jsonb NOT NULL CHECK (jsonb_typeof(mapping_config)='object'),
    last_attempted_run_at timestamptz,
    last_successful_run_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at>=created_at),
    FOREIGN KEY(affiliate_program_id,retailer_market_id) REFERENCES affiliate_program(id,retailer_market_id),
    UNIQUE(affiliate_program_id,external_feed_id),
    UNIQUE(id,retailer_market_id)
);
CREATE TABLE affiliate_feed_run (
    id uuid PRIMARY KEY,
    affiliate_feed_id uuid NOT NULL REFERENCES affiliate_feed(id),
    status varchar(32) NOT NULL CHECK (status IN ('STARTED','PROCESSING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED')),
    items_read bigint NOT NULL DEFAULT 0 CHECK (items_read>=0),
    items_accepted bigint NOT NULL DEFAULT 0 CHECK (items_accepted>=0),
    items_rejected bigint NOT NULL DEFAULT 0 CHECK (items_rejected>=0),
    products_created bigint NOT NULL DEFAULT 0 CHECK (products_created>=0),
    products_updated bigint NOT NULL DEFAULT 0 CHECK (products_updated>=0),
    offers_created bigint NOT NULL DEFAULT 0 CHECK (offers_created>=0),
    offers_updated bigint NOT NULL DEFAULT 0 CHECK (offers_updated>=0),
    offers_marked_stale bigint NOT NULL DEFAULT 0 CHECK (offers_marked_stale>=0),
    rejection_counts jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(rejection_counts)='object'),
    failure_code varchar(100) CHECK (failure_code ~ '^FEED_[A-Z_]{1,80}$'),
    started_at timestamptz NOT NULL,
    completed_at timestamptz CHECK (completed_at>=started_at),
    created_at timestamptz NOT NULL CHECK (started_at>=created_at),
    UNIQUE(id,affiliate_feed_id),
    CHECK (items_read=items_accepted+items_rejected),
    CHECK (products_created+products_updated<=items_accepted),
    CHECK (offers_created+offers_updated<=items_accepted),
    CHECK ((status IN ('COMPLETED','COMPLETED_WITH_ERRORS','FAILED'))=(completed_at IS NOT NULL)),
    CHECK ((status='FAILED')=(failure_code IS NOT NULL)),
    CHECK (status<>'COMPLETED' OR items_rejected=0),
    CHECK (status<>'COMPLETED_WITH_ERRORS' OR items_rejected>0),
    CHECK (status='COMPLETED' OR offers_marked_stale=0)
);
CREATE UNIQUE INDEX affiliate_feed_one_active_run ON affiliate_feed_run(affiliate_feed_id) WHERE status IN ('STARTED','PROCESSING');
CREATE INDEX affiliate_feed_run_history_idx ON affiliate_feed_run(affiliate_feed_id,started_at DESC);
ALTER TABLE product_offer
    ADD COLUMN last_affiliate_feed_id uuid,
    ADD COLUMN last_seen_feed_run_id uuid,
    ADD CONSTRAINT product_offer_feed_market_fk FOREIGN KEY(last_affiliate_feed_id,retailer_market_id) REFERENCES affiliate_feed(id,retailer_market_id),
    ADD CONSTRAINT product_offer_seen_run_fk FOREIGN KEY(last_seen_feed_run_id,last_affiliate_feed_id) REFERENCES affiliate_feed_run(id,affiliate_feed_id),
    ADD CONSTRAINT product_offer_feed_provenance_check CHECK ((last_affiliate_feed_id IS NULL)=(last_seen_feed_run_id IS NULL));
CREATE INDEX product_offer_feed_seen_idx ON product_offer(last_affiliate_feed_id,last_seen_feed_run_id) WHERE last_affiliate_feed_id IS NOT NULL;
