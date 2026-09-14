package com.coupleoutfit.source.api;

import com.coupleoutfit.source.application.SourceException;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SourceExceptionHandler {
    @ExceptionHandler(SourceException.class) public ResponseEntity<ProblemDetail> failure(SourceException error,HttpServletRequest request) {
        int status=switch(error.code()) {
            case "SOURCE_NOT_FOUND","SOURCE_ASSET_NOT_FOUND","SOURCE_UPLOAD_NOT_FOUND" -> 404;
            case "SOURCE_INVALID_STATE" -> 409;
            case "SOURCE_IMAGE_TOO_LARGE" -> 413;
            case "SOURCE_IMAGE_TYPE_UNSUPPORTED" -> 415;
            case "SOURCE_UPLOAD_EXPIRED" -> 410;
            case "SOURCE_REQUEST_FORBIDDEN" -> 403;
            case "SOURCE_STORAGE_UNAVAILABLE","SOURCE_VERIFICATION_BUSY" -> 503;
            default -> 400;
        };
        var detail=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),"The source request could not be completed.");
        detail.setTitle("Source request rejected"); detail.setType(URI.create("https://coupleoutfit.com/problems/"+error.code().toLowerCase(java.util.Locale.ROOT).replace('_','-')));
        detail.setProperty("code",error.code()); detail.setProperty("traceId",request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
        org.slf4j.LoggerFactory.getLogger(SourceExceptionHandler.class).atWarn().addKeyValue("errorCode",error.code()).log("Source request rejected");
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(detail);
    }
}
