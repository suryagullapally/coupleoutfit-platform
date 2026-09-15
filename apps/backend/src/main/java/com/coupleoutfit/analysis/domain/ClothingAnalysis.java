package com.coupleoutfit.analysis.domain;

import java.time.Instant;
import java.util.*;

public record ClothingAnalysis(UUID id,UUID sourceItemId,AnalysisStatus status,UUID currentRevisionId,String failureCode,
        Instant createdAt,Instant startedAt,Instant completedAt,Instant updatedAt) {
    public ClothingAnalysis {
        Objects.requireNonNull(id); Objects.requireNonNull(sourceItemId); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if(updatedAt.isBefore(createdAt) || startedAt!=null && startedAt.isBefore(createdAt) || completedAt!=null && (startedAt==null || completedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException("Invalid analysis timestamps.");
        }
        if(status!=AnalysisStatus.PENDING && startedAt==null) throw new IllegalArgumentException("Started analysis requires a timestamp.");
        if(status==AnalysisStatus.COMPLETED && (currentRevisionId==null || completedAt==null) || status!=AnalysisStatus.COMPLETED && currentRevisionId!=null) {
            throw new IllegalArgumentException("Invalid current revision state.");
        }
        if(status==AnalysisStatus.FAILED && (failureCode==null || !failureCode.matches("ANALYSIS_[A-Z_]{1,80}"))
                || status!=AnalysisStatus.FAILED && failureCode!=null) throw new IllegalArgumentException("Invalid failure state.");
    }
    public ClothingAnalysis start(Instant now) {
        if(status!=AnalysisStatus.PENDING) throw new IllegalStateException("Analysis is not pending.");
        return new ClothingAnalysis(id,sourceItemId,AnalysisStatus.IN_PROGRESS,null,null,createdAt,now,null,now);
    }
    public ClothingAnalysis complete(AnalysisRevision revision,Instant now) {
        if(status!=AnalysisStatus.IN_PROGRESS || revision.revisionType()!=RevisionType.AI_GENERATED || !id.equals(revision.clothingAnalysisId())) throw new IllegalStateException("Invalid analysis completion.");
        return new ClothingAnalysis(id,sourceItemId,AnalysisStatus.COMPLETED,revision.id(),null,createdAt,startedAt,now,now);
    }
    public ClothingAnalysis correct(AnalysisRevision revision,Instant now) {
        if(status!=AnalysisStatus.COMPLETED || revision.revisionType()!=RevisionType.USER_CORRECTED || !id.equals(revision.clothingAnalysisId()) || !currentRevisionId.equals(revision.parentRevisionId())) {
            throw new IllegalStateException("Invalid correction parent.");
        }
        return new ClothingAnalysis(id,sourceItemId,status,revision.id(),null,createdAt,startedAt,completedAt,now);
    }
    public ClothingAnalysis fail(String code,Instant now) {
        if(status!=AnalysisStatus.IN_PROGRESS) throw new IllegalStateException("Analysis is not in progress.");
        return new ClothingAnalysis(id,sourceItemId,AnalysisStatus.FAILED,null,code,createdAt,startedAt,null,now);
    }
}
