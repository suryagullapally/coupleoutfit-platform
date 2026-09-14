CREATE TABLE country (
    code varchar(2) PRIMARY KEY CHECK (code ~ '^[A-Z]{2}$'),
    name varchar(120) NOT NULL CHECK (btrim(name) <> ''),
    active boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at)
);
CREATE TABLE currency (
    code varchar(3) PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
    minor_unit_digits smallint NOT NULL CHECK (minor_unit_digits >= 0),
    active boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at)
);
CREATE TABLE locale (
    code varchar(64) PRIMARY KEY CHECK (code ~ '^[a-z]{2,8}(-[A-Za-z0-9]{1,8})*$'),
    country_code varchar(2) NOT NULL REFERENCES country(code),
    default_currency_code varchar(3) NOT NULL REFERENCES currency(code),
    active boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at),
    UNIQUE (code, country_code, default_currency_code)
);
CREATE TABLE retailer (
    id uuid PRIMARY KEY,
    slug varchar(100) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name varchar(160) NOT NULL CHECK (btrim(name) <> ''),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at)
);
CREATE TABLE retailer_market (
    id uuid PRIMARY KEY,
    retailer_id uuid NOT NULL REFERENCES retailer(id),
    country_code varchar(2) NOT NULL REFERENCES country(code),
    default_currency_code varchar(3) NOT NULL REFERENCES currency(code),
    locale_code varchar(64) NOT NULL REFERENCES locale(code),
    domain varchar(253) NOT NULL CHECK (domain ~ '^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$'),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    catalogue_enabled boolean NOT NULL,
    matching_enabled boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    UNIQUE (retailer_id, country_code),
    FOREIGN KEY (locale_code, country_code, default_currency_code)
        REFERENCES locale(code, country_code, default_currency_code)
);
CREATE INDEX retailer_market_country_status_idx ON retailer_market(country_code, status);

INSERT INTO country(code, name, active) VALUES ('GB', 'United Kingdom', true);
INSERT INTO currency(code, minor_unit_digits, active) VALUES ('GBP', 2, true);
INSERT INTO locale(code, country_code, default_currency_code, active) VALUES ('en-GB', 'GB', 'GBP', true);
