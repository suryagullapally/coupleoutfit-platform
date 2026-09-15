package com.coupleoutfit.analysis.application;

import java.util.*;

/** Private input reference; diagnostics never include the image or URL. */
public record AnalysisImage(byte[] bytes,String mediaType,String trustedImageUrl) {
    public AnalysisImage {
        if(bytes==null && trustedImageUrl==null || bytes!=null && trustedImageUrl!=null) throw new IllegalArgumentException("One image input required.");
        if(bytes!=null) {
            if(bytes.length==0 || bytes.length>10485760 || !Set.of("image/png","image/jpeg","image/webp").contains(mediaType)) throw new IllegalArgumentException("Invalid image input.");
            bytes=bytes.clone();
        }
    }
    @Override public byte[] bytes() { return bytes==null?null:bytes.clone(); }
    @Override public String toString() { return "AnalysisImage[private]"; }
}
