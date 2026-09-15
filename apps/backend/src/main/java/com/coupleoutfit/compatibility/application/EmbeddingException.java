package com.coupleoutfit.compatibility.application;
public final class EmbeddingException extends RuntimeException {
    private final String code;private final boolean transientFailure;
    public EmbeddingException(String code) {this(code,false);}
    public EmbeddingException(String code,boolean transientFailure) {super(code);this.code=code;this.transientFailure=transientFailure;}
    public String code(){return code;}public boolean transientFailure(){return transientFailure;}
}
