-- No destinations are approved by default. Configuration is internal and exact-host only.
ALTER TABLE affiliate_program ADD COLUMN approved_destination_hosts text[] NOT NULL DEFAULT '{}'
    CHECK (cardinality(approved_destination_hosts)<=20 AND array_position(approved_destination_hosts,NULL) IS NULL);
ALTER TABLE match_result ADD CONSTRAINT match_result_purchase_identity UNIQUE(id,match_session_id,product_offer_id);
ALTER TABLE product_offer ADD CONSTRAINT product_offer_market_identity UNIQUE(id,retailer_market_id);
CREATE TABLE purchase_link (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
    token_nonce varchar(64) NOT NULL UNIQUE CHECK (token_nonce ~ '^[a-f0-9]{64}$'),
    match_result_id uuid NOT NULL UNIQUE,
    match_session_id uuid NOT NULL,
    product_offer_id uuid NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    last_used_at timestamptz,
    CHECK (expires_at>created_at),
    CHECK (last_used_at>=created_at),
    FOREIGN KEY(match_result_id,match_session_id,product_offer_id) REFERENCES match_result(id,match_session_id,product_offer_id),
    UNIQUE(id,match_result_id,match_session_id,product_offer_id)
);
CREATE TABLE affiliate_click (
    id uuid PRIMARY KEY,
    purchase_link_id uuid NOT NULL,
    match_session_id uuid NOT NULL,
    match_result_id uuid NOT NULL,
    product_offer_id uuid NOT NULL,
    retailer_market_id uuid NOT NULL,
    affiliate_program_id uuid NOT NULL,
    country_code varchar(2) NOT NULL REFERENCES country(code),
    currency_code varchar(3) NOT NULL REFERENCES currency(code),
    locale_code varchar(64) NOT NULL REFERENCES locale(code),
    request_id varchar(64) CHECK (request_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'),
    destination_host varchar(253) NOT NULL CHECK (btrim(destination_host)<>''),
    destination_url_hash varchar(64) NOT NULL CHECK (destination_url_hash ~ '^[a-f0-9]{64}$'),
    status varchar(16) NOT NULL CHECK (status='REDIRECTED'),
    created_at timestamptz NOT NULL,
    redirected_at timestamptz NOT NULL CHECK (redirected_at>=created_at),
    FOREIGN KEY(purchase_link_id,match_result_id,match_session_id,product_offer_id) REFERENCES purchase_link(id,match_result_id,match_session_id,product_offer_id),
    FOREIGN KEY(product_offer_id,retailer_market_id) REFERENCES product_offer(id,retailer_market_id),
    FOREIGN KEY(affiliate_program_id,retailer_market_id) REFERENCES affiliate_program(id,retailer_market_id),
    FOREIGN KEY(locale_code,country_code,currency_code) REFERENCES locale(code,country_code,default_currency_code)
);
CREATE INDEX affiliate_click_link_time_idx ON affiliate_click(purchase_link_id,created_at);
-- Restrictive foreign keys retain attribution; retire offers/programmes rather than deleting history.
CREATE FUNCTION protect_affiliate_click() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' THEN
        RAISE EXCEPTION 'Affiliate clicks are immutable' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM match_session s JOIN retailer_market rm ON rm.id=NEW.retailer_market_id
        WHERE s.id=NEW.match_session_id AND
        (s.country_code,s.currency_code,s.locale_code)=(NEW.country_code,NEW.currency_code,NEW.locale_code) AND
        (rm.country_code,rm.default_currency_code,rm.locale_code)=(NEW.country_code,NEW.currency_code,NEW.locale_code)) THEN
        RAISE EXCEPTION 'Invalid click market' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER affiliate_click_guard BEFORE INSERT OR UPDATE ON affiliate_click FOR EACH ROW EXECUTE FUNCTION protect_affiliate_click();
