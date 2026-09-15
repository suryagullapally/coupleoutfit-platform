package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity(name="AnalysisRevisionRecord")
@Table(name="analysis_revision")
@org.hibernate.annotations.Immutable
class AnalysisRevisionEntity {
    @Id UUID id;
    UUID clothingAnalysisId;
    int revisionNumber;
    @Enumerated(EnumType.STRING) RevisionType revisionType;
    UUID parentRevisionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") ClothingAttributes attributes;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") AttributeConfidence confidence;
    @Column(length=16) String schemaVersion;
    @Column(length=32) String provider;
    @Column(length=120) String model;
    @Column(length=200) String providerRequestId;
    @Column(length=100) String promptVersion;
    Instant createdAt;
    protected AnalysisRevisionEntity() {}
    AnalysisRevisionEntity(AnalysisRevision value) {
        id=value.id();
        clothingAnalysisId=value.clothingAnalysisId();
        revisionNumber=value.revisionNumber();
        revisionType=value.revisionType();
        parentRevisionId=value.parentRevisionId();
        attributes=value.attributes();
        confidence=value.confidence();
        schemaVersion=value.schemaVersion();
        provider=value.provider();
        model=value.model();
        providerRequestId=value.providerRequestId();
        promptVersion=value.promptVersion();
        createdAt=value.createdAt();
    }
    AnalysisRevision toDomain() {return new AnalysisRevision(id,clothingAnalysisId,revisionNumber,revisionType,parentRevisionId,attributes,confidence,schemaVersion,provider,model,providerRequestId,promptVersion,createdAt);}
}
