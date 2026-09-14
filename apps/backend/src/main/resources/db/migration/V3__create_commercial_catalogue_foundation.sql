CREATE TABLE product (
    id uuid PRIMARY KEY,
    retailer_id uuid NOT NULL REFERENCES retailer(id),
    canonical_name varchar(300) NOT NULL CHECK (btrim(canonical_name) <> ''),
    brand varchar(160) CHECK (btrim(brand) <> ''),
    category varchar(32) NOT NULL CHECK (category IN ('TOP','SHIRT','T_SHIRT','KNITWEAR','DRESS','JUMPSUIT','TROUSERS','JEANS','SKIRT','SHORTS','SUIT','JACKET','COAT','FOOTWEAR','ACCESSORY','OTHER')),
    subcategory varchar(64) CHECK (subcategory ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    target varchar(16) NOT NULL CHECK (target IN ('MENSWEAR','WOMENSWEAR')),
    attributes jsonb NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    attribute_schema_version varchar(16) NOT NULL CHECK (attribute_schema_version = '1'),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    UNIQUE (id, retailer_id)
);
CREATE INDEX product_retailer_idx ON product(retailer_id);

-- Supporting keys enforce retailer ownership and the market currency without triggers.
ALTER TABLE retailer_market ADD CONSTRAINT retailer_market_id_retailer_key UNIQUE (id, retailer_id);
ALTER TABLE retailer_market ADD CONSTRAINT retailer_market_id_currency_key UNIQUE (id, default_currency_code);

CREATE TABLE product_offer (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL,
    retailer_market_id uuid NOT NULL,
    retailer_id uuid NOT NULL,
    retailer_product_id varchar(200) NOT NULL CHECK (btrim(retailer_product_id) <> '' AND retailer_product_id = btrim(retailer_product_id)),
    title varchar(300) NOT NULL CHECK (btrim(title) <> ''),
    product_url varchar(4096) NOT NULL CHECK (product_url ~* '^https?://[^[:space:]]+$'),
    affiliate_url varchar(4096) CHECK (affiliate_url ~* '^https?://[^[:space:]]+$'),
    amount_minor bigint CHECK (amount_minor >= 0),
    currency_code varchar(3) REFERENCES currency(code),
    availability_status varchar(16) NOT NULL CHECK (availability_status IN ('IN_STOCK','OUT_OF_STOCK','UNKNOWN')),
    primary_image_url varchar(4096) CHECK (primary_image_url ~* '^https?://[^[:space:]]+$'),
    active boolean NOT NULL,
    last_seen_at timestamptz,
    last_synced_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    UNIQUE (retailer_market_id, retailer_product_id),
    CHECK ((amount_minor IS NULL) = (currency_code IS NULL)),
    FOREIGN KEY (product_id, retailer_id) REFERENCES product(id, retailer_id),
    FOREIGN KEY (retailer_market_id, retailer_id) REFERENCES retailer_market(id, retailer_id),
    FOREIGN KEY (retailer_market_id, currency_code) REFERENCES retailer_market(id, default_currency_code)
);
CREATE INDEX product_offer_product_idx ON product_offer(product_id);

CREATE TABLE product_variant (
    id uuid PRIMARY KEY,
    offer_id uuid NOT NULL REFERENCES product_offer(id),
    retailer_variant_id varchar(200) CHECK (btrim(retailer_variant_id) <> '' AND retailer_variant_id = btrim(retailer_variant_id)),
    sku varchar(200) CHECK (btrim(sku) <> ''),
    size varchar(100) CHECK (btrim(size) <> ''),
    color varchar(100) CHECK (btrim(color) <> ''),
    availability_status varchar(16) NOT NULL CHECK (availability_status IN ('IN_STOCK','OUT_OF_STOCK','UNKNOWN')),
    attributes jsonb NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    last_synced_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    UNIQUE (offer_id, retailer_variant_id)
);
