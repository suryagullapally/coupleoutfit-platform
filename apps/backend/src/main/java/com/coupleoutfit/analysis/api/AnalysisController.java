package com.coupleoutfit.analysis.api;

import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.source.api.AnonymousSourceSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/source-items/{sourceId}/analysis")
public class AnalysisController {
    private final StartClothingAnalysis start;
    private final ReviewClothingAnalysis review;
    private final AnonymousSourceSession sessions;
    public AnalysisController(StartClothingAnalysis start,ReviewClothingAnalysis review,AnonymousSourceSession sessions) {
        this.start=start;this.review=review;this.sessions=sessions;
    }
    public record CorrectionRequest(UUID parentRevisionId,tools.jackson.databind.JsonNode attributes) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void reject(String name,Object value) { throw new AnalysisException("ANALYSIS_INVALID_ATTRIBUTES"); }
    }
    public record RevisionResponse(UUID id,int revisionNumber,RevisionType revisionType,String schemaVersion,
            ClothingAttributes attributes,AttributeConfidence confidence) {}
    public record AnalysisResponse(UUID id,AnalysisStatus status,RevisionResponse currentRevision,String failureCode) {}
    private ResponseEntity<AnalysisResponse> result(AnalysisSnapshot snapshot) {
        var a=snapshot.analysis();var r=snapshot.currentRevision();
        org.slf4j.LoggerFactory.getLogger(AnalysisController.class).atInfo().addKeyValue("analysisId",a.id())
                .addKeyValue("status",a.status()).log("Clothing analysis request completed");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AnalysisResponse(a.id(),a.status(),
                r==null?null:new RevisionResponse(r.id(),r.revisionNumber(),r.revisionType(),r.schemaVersion(),r.attributes(),r.confidence()),a.failureCode()));
    }
    private static ClothingAttributes attributes(tools.jackson.databind.JsonNode node) {
        try {
            if(node==null || !node.isObject() || !node.propertyNames().equals(java.util.Set.of("category","subcategory","primaryColors","secondaryColors","pattern","materialAppearance","fit","formality","seasonality","styleTags","details")))
                throw new IllegalArgumentException();
            return tools.jackson.databind.json.JsonMapper.builder()
                    .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build().treeToValue(node,ClothingAttributes.class);
        } catch(Exception invalid) { throw new AnalysisException("ANALYSIS_INVALID_ATTRIBUTES"); }
    }
    @PostMapping public ResponseEntity<AnalysisResponse> start(@PathVariable UUID sourceId,HttpServletRequest request) {
        sessions.guardWrite(request);return result(start.start(sourceId,sessions.require(request)));
    }
    @GetMapping public ResponseEntity<AnalysisResponse> get(@PathVariable UUID sourceId,HttpServletRequest request) {
        return result(review.get(sourceId,sessions.require(request)));
    }
    @PostMapping("/revisions") public ResponseEntity<AnalysisResponse> correct(@PathVariable UUID sourceId,
            @RequestBody CorrectionRequest body,HttpServletRequest request) {
        sessions.guardWrite(request);return result(review.correct(sourceId,sessions.require(request),body.parentRevisionId(),attributes(body.attributes())));
    }
}
