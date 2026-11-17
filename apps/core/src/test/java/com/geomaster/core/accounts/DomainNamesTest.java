package com.geomaster.core.accounts;

import com.geomaster.core.api.InvalidInputException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainNamesTest {

    @ParameterizedTest
    @CsvSource({
            "rideflumserberg.ch, rideflumserberg.ch",
            "  RideFlumserberg.CH  , rideflumserberg.ch",
            "https://www.rideflumserberg.ch/, rideflumserberg.ch",
            "http://WWW.Example.com/path?q=1#frag, example.com",
            "example.com:8443, example.com",
            "example.com., example.com",
            "sub.domain.example.co.uk, sub.domain.example.co.uk",
            "zürich.ch, xn--zrich-kva.ch",
            "www.xn--zrich-kva.ch, xn--zrich-kva.ch"
    })
    void normalises(String input, String expected) {
        assertEquals(expected, DomainNames.normalise(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "localhost", "example", "192.168.1.1", "-bad.ch", "bad-.ch",
            "exa mple.ch", "user:pass@example.com", "example.c", "example..ch", "example.com:abc", "https://"})
    void rejectsInvalid(String input) {
        InvalidInputException e = assertThrows(InvalidInputException.class, () -> DomainNames.normalise(input));
        assertEquals("domain", e.field());
    }
}
