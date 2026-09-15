CREATE TABLE match_session (
    id uuid PRIMARY KEY,
    source_item_id uuid NOT NULL REFERENCES source_item(id),
    analysis_revision_id uuid NOT NULL REFERENCES analysis_revision(id),
    request_key varchar(64) NOT NULL UNIQUE CHECK (request_key ~ '^[a-f0-9]{64}$'),
    country_code varchar(2) NOT NULL REFERENCES country(code),
    currency_code varchar(3) NOT NULL REFERENCES currency(code),
    locale_code varchar(64) NOT NULL REFERENCES locale(code),
    partner_target varchar(16) NOT NULL CHECK (partner_target IN ('MENSWEAR','WOMENSWEAR')),
    match_mode varchar(16) NOT NULL CHECK (match_mode IN ('EXACT','COORDINATED','COMPLEMENTARY')),
    same_retailer_only boolean NOT NULL,
    source_retailer_market_id uuid REFERENCES retailer_market(id),
    status varchar(16) NOT NULL CHECK (status IN ('CREATED','MATCHING','COMPLETED','FAILED')),
    algorithm_version varchar(100) NOT NULL CHECK (btrim(algorithm_version)<>''),
    candidate_limit integer NOT NULL CHECK (candidate_limit BETWEEN 1 AND 200),
    result_limit integer NOT NULL CHECK (result_limit BETWEEN 1 AND 50 AND result_limit<=candidate_limit),
    minimum_score smallint NOT NULL CHECK (minimum_score BETWEEN 60 AND 100),
    failure_code varchar(100) CHECK (failure_code ~ '^MATCH_[A-Z_]{1,80}$'),
    created_at timestamptz NOT NULL,
    started_at timestamptz CHECK (started_at>=created_at),
    completed_at timestamptz CHECK (completed_at>=started_at),
    updated_at timestamptz NOT NULL CHECK (updated_at>=created_at),
    FOREIGN KEY (locale_code,country_code,currency_code) REFERENCES locale(code,country_code,default_currency_code),
    CHECK (NOT same_retailer_only OR source_retailer_market_id IS NOT NULL),
    CHECK ((status='CREATED')=(started_at IS NULL)),
    CHECK ((status IN ('COMPLETED','FAILED'))=(completed_at IS NOT NULL)),
    CHECK ((status='FAILED')=(failure_code IS NOT NULL))
);
CREATE INDEX match_session_source_idx ON match_session(source_item_id);
CREATE TABLE match_result (
    id uuid PRIMARY KEY,
    match_session_id uuid NOT NULL REFERENCES match_session(id),
    product_offer_id uuid NOT NULL REFERENCES product_offer(id),
    rank integer NOT NULL CHECK (rank>0),
    couple_match_score smallint NOT NULL CHECK (couple_match_score BETWEEN 60 AND 100),
    score_band varchar(16) NOT NULL,
    semantic_score smallint NOT NULL CHECK (semantic_score BETWEEN 0 AND 100),
    color_score smallint NOT NULL CHECK (color_score BETWEEN 0 AND 100),
    style_score smallint NOT NULL CHECK (style_score BETWEEN 0 AND 100),
    formality_score smallint NOT NULL CHECK (formality_score BETWEEN 0 AND 100),
    pattern_score smallint NOT NULL CHECK (pattern_score BETWEEN 0 AND 100),
    seasonality_score smallint NOT NULL CHECK (seasonality_score BETWEEN 0 AND 100),
    category_score smallint NOT NULL CHECK (category_score BETWEEN 0 AND 100),
    explanation jsonb NOT NULL CHECK (jsonb_typeof(explanation)='object' AND explanation ? 'reasons' AND jsonb_typeof(explanation->'reasons')='array' AND jsonb_array_length(explanation->'reasons')<=3),
    algorithm_version varchar(100) NOT NULL CHECK (btrim(algorithm_version)<>''),
    created_at timestamptz NOT NULL,
    UNIQUE(match_session_id,rank),
    UNIQUE(match_session_id,product_offer_id),
    CHECK (score_band=CASE WHEN couple_match_score>=90 THEN 'EXCELLENT' WHEN couple_match_score>=80 THEN 'GREAT' WHEN couple_match_score>=70 THEN 'GOOD' ELSE 'POSSIBLE' END)
);

-- Validate cross-aggregate identity and preserve the configuration even against direct SQL.
CREATE FUNCTION protect_match_session() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF NOT EXISTS (SELECT 1 FROM source_item s JOIN clothing_analysis a ON a.source_item_id=s.id
            JOIN analysis_revision ar ON ar.clothing_analysis_id=a.id
            WHERE s.id=NEW.source_item_id AND ar.id=NEW.analysis_revision_id AND a.current_revision_id=ar.id
            AND s.status='READY_FOR_ANALYSIS' AND a.status='COMPLETED'
            AND (s.country_code,s.currency_code,s.locale_code)=(NEW.country_code,NEW.currency_code,NEW.locale_code)) THEN
            RAISE EXCEPTION 'Invalid match source revision or market' USING ERRCODE='23514';
        END IF;
        IF NEW.source_retailer_market_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM source_url u JOIN retailer_market rm ON rm.id=u.resolved_retailer_market_id
            WHERE u.source_item_id=NEW.source_item_id AND u.resolution_status='RESOLVED_CATALOGUE_OFFER'
            AND rm.id=NEW.source_retailer_market_id
            AND (rm.country_code,rm.default_currency_code,rm.locale_code)=(NEW.country_code,NEW.currency_code,NEW.locale_code)) THEN
            RAISE EXCEPTION 'Invalid source retailer market' USING ERRCODE='23514';
        END IF;
    ELSE
        IF OLD.status IN ('COMPLETED','FAILED') OR
            (to_jsonb(NEW)-ARRAY['status','started_at','completed_at','updated_at','failure_code']) IS DISTINCT FROM
            (to_jsonb(OLD)-ARRAY['status','started_at','completed_at','updated_at','failure_code']) OR
            NOT ((OLD.status='CREATED' AND NEW.status='MATCHING') OR (OLD.status='MATCHING' AND NEW.status IN ('COMPLETED','FAILED'))) THEN
            RAISE EXCEPTION 'Match session configuration and final state are immutable' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER match_session_guard BEFORE INSERT OR UPDATE ON match_session FOR EACH ROW EXECUTE FUNCTION protect_match_session();
CREATE FUNCTION protect_match_result() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' THEN
        RAISE EXCEPTION 'Match results are immutable' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM match_session s JOIN product_offer o ON o.id=NEW.product_offer_id
        JOIN retailer_market rm ON rm.id=o.retailer_market_id
        WHERE s.id=NEW.match_session_id AND s.status='MATCHING' AND s.algorithm_version=NEW.algorithm_version
        AND NEW.rank<=s.result_limit AND NEW.couple_match_score>=s.minimum_score
        AND (s.country_code,s.currency_code,s.locale_code)=(rm.country_code,rm.default_currency_code,rm.locale_code)
        AND (NOT s.same_retailer_only OR s.source_retailer_market_id=rm.id)) THEN
        RAISE EXCEPTION 'Invalid match result context' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER match_result_guard BEFORE INSERT OR UPDATE ON match_result FOR EACH ROW EXECUTE FUNCTION protect_match_result();
