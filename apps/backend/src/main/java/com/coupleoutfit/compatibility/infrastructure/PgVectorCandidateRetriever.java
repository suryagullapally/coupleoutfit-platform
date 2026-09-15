package com.coupleoutfit.compatibility.infrastructure;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.analysis.domain.ClothingAttributes;
import com.coupleoutfit.catalogue.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.TimeUnit;
@Repository
public class PgVectorCandidateRetriever implements CandidateRetrievalPort {
    private final NamedParameterJdbcTemplate jdbc;private final MeterRegistry metrics;
    private final JsonMapper json=JsonMapper.builder().build();private final CategoryEligibilityPolicy categories=new CategoryEligibilityPolicy();
    public PgVectorCandidateRetriever(NamedParameterJdbcTemplate jdbc,MeterRegistry metrics){this.jdbc=jdbc;this.metrics=metrics;}
    static final String SQL="""
        WITH eligible AS (
            SELECT p.id AS product_id,o.id AS offer_id,rm.id AS retailer_market_id,p.category,p.target,p.attributes,o.availability_status,
                pe.embedding <=> CAST(:vector AS vector) AS distance,
                row_number() OVER (PARTITION BY p.id ORDER BY CASE o.availability_status WHEN 'IN_STOCK' THEN 0 ELSE 1 END,o.id,pe.created_at DESC) AS offer_choice
            FROM product_embedding pe
            JOIN product p ON p.id=pe.product_id AND pe.source_updated_at=p.updated_at
            JOIN product_offer o ON o.product_id=p.id
            JOIN retailer_market rm ON rm.id=o.retailer_market_id
            JOIN retailer r ON r.id=rm.retailer_id
            JOIN country c ON c.code=rm.country_code
            JOIN currency cu ON cu.code=rm.default_currency_code
            JOIN locale l ON l.code=rm.locale_code
            WHERE pe.embedding IS NOT NULL AND pe.embedding_type='STYLE_COMPATIBILITY'
                AND pe.provider=:provider AND pe.model=:model AND pe.dimensions=:dimensions AND pe.descriptor_version=:descriptorVersion
                AND rm.country_code=:country AND rm.default_currency_code=:currency AND rm.locale_code=:locale
                AND (CAST(:retailerRestriction AS uuid) IS NULL OR rm.id=CAST(:retailerRestriction AS uuid))
                AND c.active AND cu.active AND l.active AND r.status='ACTIVE'
                AND rm.status='ACTIVE' AND rm.catalogue_enabled AND rm.matching_enabled
                AND p.status='ACTIVE' AND p.target=:target AND p.category IN (:categories)
                AND o.active AND o.availability_status IN ('IN_STOCK','UNKNOWN')
                AND (o.amount_minor IS NULL OR o.currency_code=:currency)
        )
        SELECT * FROM eligible WHERE offer_choice=1 ORDER BY distance,product_id LIMIT :limit
        """;
    @Override public List<CompatibilityCandidate> retrieve(CompatibilityRequest request,AnalysisEmbedding embedding,ClothingAttributes source) {
        if(!request.analysisRevisionId().equals(embedding.analysisRevisionId()) || request.sourceCategory()!=source.category())throw new EmbeddingException("INVALID_COMPATIBILITY_REQUEST");
        long start=System.nanoTime();metrics.counter("candidate.retrieval.count").increment();
        try {
            var data=embedding.data();var market=request.marketContext();
            var p=new MapSqlParameterSource().addValue("vector",VectorStorage.encode(data.vector())).addValue("provider",data.model().provider()).addValue("model",data.model().model())
                    .addValue("dimensions",data.model().dimensions()).addValue("descriptorVersion",data.descriptorVersion()).addValue("country",market.countryCode())
                    .addValue("currency",market.currencyCode()).addValue("locale",market.localeCode()).addValue("target",request.target().name())
                    .addValue("categories",categories.allowedFor(request.sourceCategory()).stream().map(Enum::name).toList()).addValue("limit",request.candidateLimit()).addValue("retailerRestriction",request.retailerMarketRestriction());
            var result=jdbc.query(SQL,p,(rs,n)->{
                double similarity=Math.clamp(1-rs.getDouble("distance"),0,1);
                var category=ProductCategory.valueOf(rs.getString("category"));var attributes=new ProductAttributes(json.readValue(rs.getString("attributes"),new tools.jackson.core.type.TypeReference<Map<String,List<String>>>(){}));
                return new CompatibilityCandidate(rs.getObject("product_id",UUID.class),rs.getObject("offer_id",UUID.class),rs.getObject("retailer_market_id",UUID.class),similarity,
                        category,ProductTarget.valueOf(rs.getString("target")),attributes,AvailabilityStatus.valueOf(rs.getString("availability_status")),CompatibilityFeatures.from(similarity,source,category,attributes));
            });
            if(result.isEmpty())metrics.counter("candidate.retrieval.empty").increment();
            org.slf4j.LoggerFactory.getLogger(PgVectorCandidateRetriever.class).atInfo().addKeyValue("candidateCount",result.size()).addKeyValue("target",request.target()).addKeyValue("marketCountry",market.countryCode()).log("Compatibility candidates retrieved");
            return List.copyOf(result);
        } catch(RuntimeException e){throw new EmbeddingException("CANDIDATE_RETRIEVAL_FAILED");}
        finally {metrics.timer("candidate.retrieval.duration").record(System.nanoTime()-start,TimeUnit.NANOSECONDS);}
    }
}
