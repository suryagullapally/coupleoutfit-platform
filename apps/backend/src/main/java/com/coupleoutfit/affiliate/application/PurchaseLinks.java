package com.coupleoutfit.affiliate.application;

import com.coupleoutfit.affiliate.domain.*;
import java.util.Set;
import java.util.UUID;

public final class PurchaseLinks {
    private final PurchaseRepository repository;
    public PurchaseLinks(PurchaseRepository repository) { this.repository=repository; }
    public record Purchase(boolean available, String url) {
        @Override public String toString() { return "Purchase[available="+available+"]"; }
    }
    public Purchase forResult(UUID resultId) {
        try { String url=repository.purchaseUrl(resultId); return new Purchase(url!=null,url); }
        catch(PurchaseException error) { throw error; }
        catch(RuntimeException error) { throw new PurchaseException("PURCHASE_TRACKING_FAILED"); }
    }
    public AffiliateDestinationPolicy.Destination redirect(String token, String requestId) {
        try { return repository.recordRedirect(token,requestId); }
        catch(PurchaseException error) { throw error; }
        catch(RuntimeException error) { throw new PurchaseException("PURCHASE_TRACKING_FAILED"); }
    }
    public void revoke(UUID id) { repository.revoke(id); }
    public void approveDestinationHosts(UUID programId, Set<String> hosts) { repository.approveDestinationHosts(programId,hosts); }
}
