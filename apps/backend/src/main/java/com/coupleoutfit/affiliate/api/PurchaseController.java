package com.coupleoutfit.affiliate.api;

import com.coupleoutfit.affiliate.application.PurchaseLinks;
import com.coupleoutfit.affiliate.domain.PurchaseException;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Locale;

@RestController
@RequestMapping("/go")
public class PurchaseController {
    private final PurchaseLinks purchases;
    private final MeterRegistry metrics;
    public PurchaseController(PurchaseLinks purchases,MeterRegistry metrics) { this.purchases=purchases; this.metrics=metrics; }
    @RequestMapping(value="/{token}",method=RequestMethod.HEAD)
    public ResponseEntity<Void> head() { return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
    @GetMapping("/{token}") public ResponseEntity<Void> redirect(@PathVariable String token,HttpServletRequest request) {
        metrics.counter("affiliate.click.requested").increment();
        String agent=request.getHeader("User-Agent");
        String purpose=request.getHeader("Sec-Purpose"); if(purpose==null) purpose=request.getHeader("Purpose");
        if(agent!=null && agent.toLowerCase(Locale.ROOT).matches(".*(bot|crawler|spider|preview).*" )
                || purpose!=null && purpose.toLowerCase(Locale.ROOT).contains("prefetch"))
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        try {
            var destination=purchases.redirect(token,(String)request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
            metrics.counter("affiliate.click.redirected").increment();
            return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer")
                    .location(URI.create(destination.url())).build();
        } catch(PurchaseException error) {
            metrics.counter(error.code().equals("PURCHASE_TRACKING_FAILED")?"affiliate.click.failed":"affiliate.click.blocked").increment();
            throw error;
        }
    }
}
