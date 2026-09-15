package com.coupleoutfit.compatibility.domain;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
public record EmbeddingDescriptor(String version,String text) {
    public EmbeddingDescriptor {
        if(version==null || !version.matches("[a-z0-9-]{1,80}") || text==null || text.isBlank() || text.length()>12000 || !text.startsWith("descriptor: "+version+"\n"))
            throw new IllegalArgumentException("Invalid embedding descriptor.");
    }
    public String hash() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @Override public String toString() { return "EmbeddingDescriptor[version="+version+"]"; }
}
