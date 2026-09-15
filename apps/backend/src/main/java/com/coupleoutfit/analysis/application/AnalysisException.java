package com.coupleoutfit.analysis.application;

public final class AnalysisException extends RuntimeException {
    private final String code;
    private final boolean transientFailure;
    public AnalysisException(String code) { this(code,false); }
    public AnalysisException(String code,boolean transientFailure) { super(code);this.code=code;this.transientFailure=transientFailure; }
    public String code() { return code; }
    public boolean transientFailure() { return transientFailure; }
}
