package com.coupleoutfit.affiliate.domain;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** A stored random nonce plus an external key permits stable reuse without storing a bearer token. */
public final class PurchaseTokens {
    private final byte[] key;
    private final SecureRandom random=new SecureRandom();
    public PurchaseTokens(byte[] key) {
        if(key==null || key.length<32) throw new IllegalArgumentException("Purchase token key requires at least 256 bits.");
        this.key=key.clone();
    }
    public String newNonce() { byte[] bytes=new byte[32]; random.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    public String token(String nonce) {
        if(nonce==null || !nonce.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid nonce.");
        try {
            var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("coupleoutfit-purchase-v1:"+nonce).getBytes(StandardCharsets.US_ASCII)));
        } catch(GeneralSecurityException error) { throw new IllegalStateException("Purchase token generation unavailable."); }
    }
    public static String lookupHash(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new PurchaseException("PURCHASE_LINK_NOT_FOUND");
        return hash(token);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException error) { throw new IllegalStateException("Purchase hashing unavailable."); }
    }
}
