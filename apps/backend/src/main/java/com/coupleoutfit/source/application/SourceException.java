package com.coupleoutfit.source.application;

public final class SourceException extends RuntimeException {
    private final String code;
    public SourceException(String code) { super(code); this.code=code; }
    public String code() { return code; }
}
