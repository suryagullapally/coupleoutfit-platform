package com.coupleoutfit.affiliate.domain;
import java.time.Instant;
import java.util.Objects;
final class AffiliateValidation {
    private AffiliateValidation() {}
    static String text(String value,int max,boolean required) {
        if(value==null){if(required)throw new IllegalArgumentException("Required integration value.");return null;}
        value=value.strip();if(value.isEmpty() || value.length()>max || value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid integration value.");
        return value;
    }
    static void times(Instant created,Instant updated) {Objects.requireNonNull(created);Objects.requireNonNull(updated);if(updated.isBefore(created))throw new IllegalArgumentException("Invalid timestamps.");}
}
