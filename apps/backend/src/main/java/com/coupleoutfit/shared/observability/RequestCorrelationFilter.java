package com.coupleoutfit.shared.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String id = inbound != null && SAFE_ID.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        String previous = MDC.get("requestId");
        MDC.put("requestId", id);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // Query strings, bodies, cookies and authorization headers are deliberately excluded.
            String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
            if (path.length() > 512) path = path.substring(0, 512);
            log.atInfo().addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("httpPath", path).addKeyValue("httpStatus", response.getStatus())
                .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
                .log("HTTP {} {} status={} durationMs={}", request.getMethod(), path,
                     response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }
}
