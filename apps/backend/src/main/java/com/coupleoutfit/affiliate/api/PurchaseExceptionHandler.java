package com.coupleoutfit.affiliate.api;

import com.coupleoutfit.affiliate.domain.PurchaseException;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Locale;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PurchaseExceptionHandler {
    @ExceptionHandler(PurchaseException.class)
    public ResponseEntity<ProblemDetail> failure(PurchaseException error,HttpServletRequest request) {
        int status=switch(error.code()) {
            case "PURCHASE_LINK_NOT_FOUND" -> 404;
            case "PURCHASE_TRACKING_FAILED" -> 503;
            default -> 410;
        };
        var problem=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),"The purchase link is currently unavailable.");
        problem.setTitle("Purchase unavailable"); problem.setInstance(URI.create("/go"));
        problem.setType(URI.create("https://coupleoutfit.com/problems/"+error.code().toLowerCase(Locale.ROOT).replace('_','-')));
        problem.setProperty("code",error.code()); problem.setProperty("traceId",request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}
