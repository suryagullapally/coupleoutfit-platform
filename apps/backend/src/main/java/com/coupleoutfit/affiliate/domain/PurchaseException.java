package com.coupleoutfit.affiliate.domain;

public final class PurchaseException extends RuntimeException {
    private final String code;
    public PurchaseException(String code) { super(code); this.code=code; }
    public String code() { return code; }
}
