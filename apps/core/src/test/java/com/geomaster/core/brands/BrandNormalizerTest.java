package com.geomaster.core.brands;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Matching keys (extraction-analysis REQ-12); must agree with the V6 backfill contract. */
class BrandNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Ride Flumserberg              | rideflumserberg
            RIDE-Flumserberg AG           | rideflumserberg
            Ride Flumserberg GmbH & Co. KG | rideflumserberg
            Skischule Flumserberg GmbH    | skischuleflumserberg
            Hôtel Suisse Sàrl             | hotelsuisse
            Café Zürich S.A.              | cafezurich
            Viajes Martín S.L.            | viajesmartin
            Straße & Co                   | strasse
            Acme Inc.                     | acme
            AG                            | ag
            '  '                          | ''
            """)
    void names(String raw, String key) {
        assertEquals(key, BrandNormalizer.name(raw));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            rideflumserberg.ch                    | rideflumserberg
            https://www.ride-flumserberg.ch/de?x=1 | rideflumserberg
            RIDE-FLUMSERBERG.CH.                  | rideflumserberg
            shop.example.de:8080                  | shopexample
            localhost                             | localhost
            """)
    void domains(String raw, String key) {
        assertEquals(key, BrandNormalizer.domain(raw));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ride-flumserberg.ch | Ride Flumserberg
            booking.com         | Booking
            """)
    void displayNames(String domain, String name) {
        assertEquals(name, BrandRepository.displayName(domain));
    }
}
