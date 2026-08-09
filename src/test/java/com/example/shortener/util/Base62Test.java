package com.example.shortener.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62Test {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "9, 9",
            "10, a",              // digits run out, lowercase starts
            "35, z",
            "36, A",              // lowercase runs out, uppercase starts
            "61, Z",              // last single character
            "62, 10",             // rolls over to two
            "1000000, 4c92",      // first id the table hands out
            "9223372036854775807, aZl8N0y58M7"   // Long.MAX_VALUE
    })
    @DisplayName("encodes known values")
    void encodesKnownValues(long number, String expected) {
        assertThat(Base62.encode(number)).isEqualTo(expected);
    }

    @Test
    @DisplayName("negative numbers are rejected")
    void rejectsNegative() {
        assertThatThrownBy(() -> Base62.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // this is the property the whole design leans on
    @Test
    @DisplayName("different ids never produce the same code")
    void differentIdsGiveDifferentCodes() {
        Set<String> codes = new HashSet<>();
        for (long id = 1_000_000; id < 1_010_000; id++) {
            assertThat(codes.add(Base62.encode(id)))
                    .as("duplicate code for id %d", id)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("codes are url safe")
    void codesAreUrlSafe() {
        for (long id = 1_000_000; id < 1_001_000; id++) {
            assertThat(Base62.encode(id)).matches("[0-9a-zA-Z]+");
        }
    }
}