package com.coupleoutfit.market.api;

import com.coupleoutfit.market.application.MarketConfigurationException;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MarketExceptionHandler {
    @ExceptionHandler(MarketConfigurationException.class)
    public ProblemDetail invalidMarket(HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The public market is currently unavailable.");
        problem.setTitle("Market configuration invalid");
        problem.setType(URI.create("https://coupleoutfit.com/problems/market-configuration-invalid"));
        problem.setProperty("code", "MARKET_CONFIGURATION_INVALID");
        problem.setProperty("traceId", request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
        return problem;
    }
}
