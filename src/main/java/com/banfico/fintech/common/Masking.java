package com.banfico.fintech.common;

/** Truncates identifiers before they go into logs — a session id is a bearer credential too. */
public final class Masking {

    private Masking() {
    }

    public static String truncate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 8 ? value : value.substring(0, 8) + "...";
    }
}
