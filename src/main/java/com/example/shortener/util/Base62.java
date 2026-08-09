package com.example.shortener.util;

public final class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("cannot encode a negative number: " + number);
        }
        if (number == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder code = new StringBuilder();
        while (number > 0) {
            code.append(ALPHABET.charAt((int) (number % BASE)));
            number /= BASE;
        }
        // digits come out least significant first
        return code.reverse().toString();
    }
}