package com.coupleoutfit.shared.api;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        boolean validation = ex instanceof MethodArgumentNotValidException
                || ex instanceof HandlerMethodValidationException;
        String code = validation ? "VALIDATION_FAILED" : "HTTP_" + status.value();
        String title = validation ? "Validation failed" : HttpStatus.valueOf(status.value()).getReasonPhrase();
        String detail = validation ? "One or more request fields are invalid." : "The request could not be processed.";
        ProblemDetail problem = problem(status, code, title, detail,
                ((ServletWebRequest) request).getRequest());
        return new ResponseEntity<>(problem, headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> validation(ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Validation failed", "One or more request fields are invalid.", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        // Exception messages can contain SQL, credentials or submitted values.
        log.error("Unhandled request failure exceptionType={}", ex.getClass().getName());
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Internal server error", "An unexpected error occurred.", request));
    }

    private ProblemDetail problem(HttpStatusCode status, String code, String title,
                                  String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://coupleoutfit.com/problems/" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        problem.setProperty("traceId", request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
        return problem;
    }
}
