package com.coupleoutfit.market.application;

public final class MarketConfigurationException extends RuntimeException {
    public MarketConfigurationException() {
        super("The configured public market is invalid or inactive.");
    }
}
