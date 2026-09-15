package com.coupleoutfit.analysis.api;

import com.coupleoutfit.analysis.application.AnalysisException;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalysisExceptionHandler {
    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ProblemDetail> failure(AnalysisException error,HttpServletRequest request) {
        int status=switch(error.code()) {
            case "ANALYSIS_NOT_FOUND" -> 404;
            case "ANALYSIS_SOURCE_NOT_READY","ANALYSIS_REVISION_CONFLICT" -> 409;
            case "ANALYSIS_INVALID_ATTRIBUTES" -> 400;
            case "ANALYSIS_IMAGE_UNSUPPORTED" -> 422;
            default -> 503;
        };
        var detail=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),"The clothing analysis request could not be completed.");
        detail.setTitle("Clothing analysis unavailable");
        detail.setType(URI.create("https://coupleoutfit.com/problems/"+error.code().toLowerCase(java.util.Locale.ROOT).replace('_','-')));
        detail.setProperty("code",error.code());detail.setProperty("traceId",request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
        org.slf4j.LoggerFactory.getLogger(AnalysisExceptionHandler.class).atWarn().addKeyValue("errorCode",error.code()).log("Clothing analysis rejected");
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(detail);
    }
}
