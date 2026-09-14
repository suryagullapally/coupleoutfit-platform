package com.coupleoutfit.source.api;

import com.coupleoutfit.source.application.SourceException;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;

@Component
public final class AnonymousSourceSession {
    public static final String COOKIE="coupleoutfit_session";
    private final byte[] key;
    private final Clock clock;
    private final Duration lifetime;
    private final boolean secure;
    private final String publicOrigin;
    public AnonymousSourceSession(Environment environment, Clock sourceClock,
            @Value("${coupleoutfit.source.session.signing-key:}") String configuredKey,
            @Value("${coupleoutfit.source.session.lifetime:P7D}") Duration lifetime,
            @Value("${coupleoutfit.source.public-origin:http://localhost:8080}") String publicOrigin) {
        clock=sourceClock; this.lifetime=lifetime; this.publicOrigin=publicOrigin;
        secure=!environment.matchesProfiles("local | test") || environment.matchesProfiles("staging | production");
        if(lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofDays(30))>0) throw new IllegalArgumentException("Invalid anonymous session lifetime.");
        if(configuredKey.isBlank() && !secure) { key=new byte[32]; new SecureRandom().nextBytes(key); }
        else {
            if(configuredKey.length()<32) throw new IllegalStateException("A source session signing key of at least 32 characters is required.");
            key=configuredKey.getBytes(StandardCharsets.UTF_8);
        }
    }
    public void guardWrite(HttpServletRequest request) {
        // A cross-site browser cannot add this header without a permitted CORS preflight.
        // Source writes are same-origin; CORS credentials remain disabled.
        String origin=request.getHeader("Origin");
        if(!"1".equals(request.getHeader("X-Source-Request")) || origin!=null && !origin.equals(publicOrigin)
                || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) throw new SourceException("SOURCE_REQUEST_FORBIDDEN");
    }
    private String token(HttpServletRequest request) {
        if(request.getCookies()==null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }
    private boolean valid(String token) {
        try {
            if(token==null || token.length()>200) return false;
            String[] parts=token.split("[.]");
            if(parts.length!=3 || !parts[0].matches("[A-Za-z0-9_-]{43}")) return false;
            long expiry=Long.parseLong(parts[1]);
            if(expiry<=clock.instant().getEpochSecond() || expiry>clock.instant().plus(lifetime).getEpochSecond()) return false;
            return MessageDigest.isEqual(sign(parts[0]+"."+parts[1]),Base64.getUrlDecoder().decode(parts[2]));
        } catch(Exception e) { return false; }
    }
    private byte[] sign(String payload) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch(GeneralSecurityException e) { throw new IllegalStateException("Session signing unavailable."); }
    }
    private String owner(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch(GeneralSecurityException e) { throw new IllegalStateException("Session hashing unavailable."); }
    }
    public String createOrRead(HttpServletRequest request,HttpServletResponse response) {
        String token=token(request);
        if(!valid(token)) {
            byte[] random=new byte[32]; new SecureRandom().nextBytes(random);
            String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(random)+"."+clock.instant().plus(lifetime).getEpochSecond();
            token=payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
            response.addHeader("Set-Cookie",ResponseCookie.from(COOKIE,token).httpOnly(true).secure(secure).sameSite("Strict")
                    .path("/api/v1/source-items").maxAge(lifetime).build().toString());
        }
        return owner(token);
    }
    public String require(HttpServletRequest request) {
        String token=token(request);
        if(!valid(token)) throw new SourceException("SOURCE_NOT_FOUND");
        return owner(token);
    }
}
