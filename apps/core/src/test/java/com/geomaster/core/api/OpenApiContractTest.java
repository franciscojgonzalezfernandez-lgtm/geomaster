package com.geomaster.core.api;

import com.geomaster.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Contract drift check (ADR-0016): the OpenAPI document springdoc generates from the running core
 * must equal the committed {@code packages/api-contract/openapi.yaml}. Regenerate with
 * {@code ./gradlew :core:updateApiContract} and commit the result in the same PR as the endpoint
 * change.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    static final Path CONTRACT = Path.of("../../packages/api-contract/openapi.yaml");
    static final String HEADER = """
            # Contract between apps/web and apps/core (ADR-0016). GENERATED — do not edit by hand.
            # Source: springdoc from apps/core. Regenerate: ./gradlew :core:updateApiContract
            # CI (OpenApiContractTest) fails when this file drifts from the code.
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void committedContractMatchesTheCode() throws Exception {
        String generated = HEADER + mvc.perform(get("/v3/api-docs.yaml"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if (Boolean.getBoolean("updateApiContract")) {
            Files.writeString(CONTRACT, generated, StandardCharsets.UTF_8);
            return;
        }
        String committed = Files.readString(CONTRACT, StandardCharsets.UTF_8);
        assertEquals(committed, generated,
                "packages/api-contract/openapi.yaml is out of date — run ./gradlew :core:updateApiContract and commit it");
    }
}
