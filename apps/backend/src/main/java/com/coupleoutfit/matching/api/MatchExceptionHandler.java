package com.coupleoutfit.matching.api;
import com.coupleoutfit.matching.application.MatchException;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MatchExceptionHandler {
    @ExceptionHandler(MatchException.class) public ResponseEntity<ProblemDetail> failure(MatchException error,HttpServletRequest request) {
        int status=switch(error.code()) {
            case "MATCH_SESSION_NOT_FOUND"->404;
            case "MATCH_SOURCE_NOT_READY","MATCH_ANALYSIS_NOT_COMPLETED","MATCH_REVISION_NOT_CURRENT","MATCH_IDEMPOTENCY_CONFLICT"->409;
            case "SAME_RETAILER_NOT_AVAILABLE"->422;
            case "MATCH_REQUEST_INVALID"->400;
            default->500;
        };
        var problem=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),"The match request could not be completed.");
        problem.setTitle("Match unavailable");problem.setType(URI.create("https://coupleoutfit.com/problems/"+error.code().toLowerCase(java.util.Locale.ROOT).replace('_','-')));
        problem.setProperty("code",error.code());problem.setProperty("traceId",request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}
