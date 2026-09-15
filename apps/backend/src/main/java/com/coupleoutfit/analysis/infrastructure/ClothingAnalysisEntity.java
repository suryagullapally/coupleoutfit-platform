package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity(name="ClothingAnalysisRecord")
@Table(name="clothing_analysis")
class ClothingAnalysisEntity {
    @Id UUID id;
    UUID sourceItemId;
    @Enumerated(EnumType.STRING) AnalysisStatus status;
    UUID currentRevisionId;
    @Column(length=100) String failureCode;
    Instant createdAt;
    Instant startedAt;
    Instant completedAt;
    Instant updatedAt;
    protected ClothingAnalysisEntity() {}
    ClothingAnalysisEntity(ClothingAnalysis value) { update(value); }
    void update(ClothingAnalysis value) {
        id=value.id();
        sourceItemId=value.sourceItemId();
        status=value.status();
        currentRevisionId=value.currentRevisionId();
        failureCode=value.failureCode();
        createdAt=value.createdAt();
        startedAt=value.startedAt();
        completedAt=value.completedAt();
        updatedAt=value.updatedAt();
    }
    ClothingAnalysis toDomain() {return new ClothingAnalysis(id,sourceItemId,status,currentRevisionId,failureCode,createdAt,startedAt,completedAt,updatedAt);}
}
