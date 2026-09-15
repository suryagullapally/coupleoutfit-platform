package com.coupleoutfit.compatibility.infrastructure;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.util.*;
@Repository
public class JdbcEmbeddingRepository implements EmbeddingRepository {
    private final NamedParameterJdbcTemplate jdbc;private final io.micrometer.core.instrument.MeterRegistry metrics;
    public JdbcEmbeddingRepository(NamedParameterJdbcTemplate jdbc,io.micrometer.core.instrument.MeterRegistry metrics){this.jdbc=jdbc;this.metrics=metrics;}
    private String table(EmbeddingSource source){return source.kind()==EmbeddingSource.Kind.PRODUCT?"product_embedding":"analysis_embedding";}
    private String subject(EmbeddingSource source){return source.kind()==EmbeddingSource.Kind.PRODUCT?"product_id":"analysis_revision_id";}
    private MapSqlParameterSource params(EmbeddingSource source,EmbeddingModelMetadata model,EmbeddingDescriptor descriptor) {
        if(model.dimensions()!=VectorStorage.DIMENSIONS)throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
        return new MapSqlParameterSource().addValue("subject",source.id()).addValue("provider",model.provider()).addValue("model",model.model())
                .addValue("dimensions",model.dimensions()).addValue("version",descriptor.version()).addValue("sourceVersion",source.sourceVersion())
                .addValue("hash",descriptor.hash()).addValue("sourceUpdated",Timestamp.from(source.sourceUpdatedAt()));
    }
    @Override @Transactional public Claim claim(EmbeddingSource source,EmbeddingModelMetadata model,EmbeddingDescriptor descriptor) {
        var id=UUID.randomUUID();var p=params(source,model,descriptor).addValue("id",id);
        int inserted=jdbc.update("INSERT INTO "+table(source)+" (id,"+subject(source)+",embedding_type,provider,model,dimensions,descriptor_version,source_version,descriptor_hash,source_updated_at,created_at,updated_at) "
                +"VALUES (:id,:subject,'STYLE_COMPATIBILITY',:provider,:model,:dimensions,:version,:sourceVersion,:hash,:sourceUpdated,now(),now()) ON CONFLICT DO NOTHING",p);
        if(inserted==1)return new Claim(id,true,null);
        var rows=jdbc.query("SELECT * FROM "+table(source)+" WHERE "+subject(source)+"=:subject AND embedding_type='STYLE_COMPATIBILITY' AND provider=:provider AND model=:model AND descriptor_version=:version AND source_version=:sourceVersion",p,
                (rs,n)->new Claim(rs.getObject("id",UUID.class),false,rs.getString("embedding")==null?null:data(rs)));
        if(rows.isEmpty())throw new EmbeddingException("EMBEDDING_NOT_AVAILABLE");return rows.getFirst();
    }
    @Override @Transactional public EmbeddingData complete(UUID id,EmbeddingSource source,EmbeddingModelMetadata model,EmbeddingDescriptor descriptor,EmbeddingProvider.Result result) {
        if(!model.equals(result.model()))throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
        var p=params(source,model,descriptor).addValue("id",id).addValue("vector",VectorStorage.encode(result.vector())).addValue("requestId",result.providerRequestId());
        var rows=jdbc.query("UPDATE "+table(source)+" SET embedding=CAST(:vector AS vector),provider_request_id=:requestId,completed_at=now(),updated_at=now() WHERE id=:id AND embedding IS NULL RETURNING *",p,(rs,n)->data(rs));
        if(rows.size()!=1)throw new EmbeddingException("EMBEDDING_NOT_AVAILABLE");
        metrics.counter(source.kind()==EmbeddingSource.Kind.PRODUCT?"embedding.product.generated":"embedding.analysis.generated").increment();return rows.getFirst();
    }
    @Override @Transactional public void abandon(UUID id,EmbeddingSource source) {
        jdbc.update("DELETE FROM "+table(source)+" WHERE id=:id AND embedding IS NULL",Map.of("id",id));
        metrics.counter("embedding.failure").increment();
    }
    private EmbeddingData data(ResultSet rs) throws SQLException {
        return new EmbeddingData(new EmbeddingModelMetadata(rs.getString("provider"),rs.getString("model"),rs.getInt("dimensions")),EmbeddingType.valueOf(rs.getString("embedding_type")),
                rs.getString("descriptor_version"),rs.getString("source_version"),rs.getString("descriptor_hash"),VectorStorage.decode(rs.getString("embedding")),rs.getString("provider_request_id"),rs.getTimestamp("completed_at").toInstant());
    }
}
