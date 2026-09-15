package com.coupleoutfit.matching.application;
public final class MatchException extends RuntimeException {
    private final String code;
    public MatchException(String code) { super(code);this.code=code; }
    public String code() {return code;}
}
