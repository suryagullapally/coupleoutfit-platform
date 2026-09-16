package com.coupleoutfit.feed.domain;
public final class FeedException extends RuntimeException {
    private final String code;
    public FeedException(String code){super(code);if(!code.matches("FEED_[A-Z_]{1,80}"))throw new IllegalArgumentException("Invalid error code.");this.code=code;}
    public String code(){return code;}
}
