package com.coupleoutfit.affiliate.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/** Exact approved hosts only. Never performs DNS lookups or follows redirects. */
public final class AffiliateDestinationPolicy {
    public record Destination(String url, String host, String urlHash) {
        @Override public String toString() { return "Destination[host="+host+"]"; }
    }
    public Set<String> approvedHosts(Set<String> hosts) {
        if(hosts==null || hosts.size()>20) throw new IllegalArgumentException("Invalid destination hosts.");
        var result=new TreeSet<String>();
        for(String host:hosts) {
            if(host==null) throw new IllegalArgumentException("Invalid destination host.");
            String normalized=host.toLowerCase(Locale.ROOT);
            if(!validHost(normalized)) throw new IllegalArgumentException("Invalid destination host.");
            result.add(normalized);
        }
        return Set.copyOf(result);
    }
    private boolean validHost(String host) {
        if(host.length()>253 || !host.matches("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?")) return false;
        return !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal")
                && !host.endsWith(".home") && !host.endsWith(".test") && !host.endsWith(".invalid");
    }
    public Destination validate(String raw, String retailerHost, Set<String> networkHosts) {
        if(raw==null || raw.isBlank()) throw new PurchaseException("PURCHASE_DESTINATION_UNAVAILABLE");
        try {
            if(raw.length()>4096 || raw.chars().anyMatch(Character::isISOControl) || raw.indexOf('\\')>=0
                    || raw.toLowerCase(Locale.ROOT).matches(".*%(?:0[ad]|00).*")) throw new IllegalArgumentException();
            var uri=new URI(raw);
            String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
            String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
            if(!Set.of("http","https").contains(scheme) || !validHost(host) || uri.getRawUserInfo()!=null || uri.getRawFragment()!=null
                    || uri.getPort()!=-1 && uri.getPort()!=(scheme.equals("https")?443:80)) throw new IllegalArgumentException();
            Set<String> approved=approvedHosts(networkHosts);
            String retailer=retailerHost.toLowerCase(Locale.ROOT);
            if(!validHost(retailer) || !host.equals(retailer) && !host.equals("www."+retailer) && !approved.contains(host)) throw new IllegalArgumentException();
            String path=uri.getRawPath().isEmpty()?"/":uri.getRawPath();
            String normalized=scheme+"://"+host+(uri.getPort()==-1?"":":"+uri.getPort())+path+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
            return new Destination(normalized,host,PurchaseTokens.hash(normalized));
        } catch(URISyntaxException | IllegalArgumentException error) { throw new PurchaseException("PURCHASE_DESTINATION_INVALID"); }
    }
}
