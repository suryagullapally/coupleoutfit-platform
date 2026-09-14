package com.coupleoutfit.source.domain;

import java.net.URI;
import java.util.Locale;

public final class SourceUrls {
    private SourceUrls() {}
    public static String normalize(String value) {
        try {
            if (value == null || value.isBlank() || value.length()>4096 || !value.equals(value.strip())) throw new IllegalArgumentException();
            URI uri = new URI(value);
            String scheme=uri.getScheme().toLowerCase(Locale.ROOT), host=uri.getHost().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getRawUserInfo()!=null || uri.getPort()>65535 || uri.getPort()==0
                    || !host.contains(".") || host.endsWith(".") || host.endsWith(".localhost") || host.endsWith(".local")
                    || host.contains(":") || host.matches("[0-9.]+")) throw new IllegalArgumentException();
            int port=uri.getPort();
            String authority=host+((port==-1 || scheme.equals("https") && port==443 || scheme.equals("http") && port==80)?"":":"+port);
            String path=uri.getRawPath().isEmpty()?"/":uri.getRawPath();
            return new URI(scheme+"://"+authority+path+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())).toASCIIString();
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid source URL."); }
    }
}
