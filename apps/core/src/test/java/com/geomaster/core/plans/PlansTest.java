package com.geomaster.core.plans;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlansTest {

    private static final String FREE = """
            {"id":"free","selfServe":true,"priceChfMonthly":0,"domains":1,"prompts":12,"engines":1,"languages":1,
             "scanFrequency":"once","competitorsPerDomain":3,"artifactsPerMonth":0,"seats":1,"historyMonths":0,
             "consultingMinutesPerMonth":0}""";

    @Test
    void classpathCopyMatchesTheSourceOfTruth() throws Exception {
        // The build copies packages/plans/plans.json; the loaded plans must equal the repo file.
        Path source = Path.of("../../packages/plans/plans.json");
        Plans fromSource;
        try (InputStream in = Files.newInputStream(source)) {
            fromSource = Plans.parse(in);
        }
        Plans loaded = Plans.fromClasspath();
        for (String id : new String[] {"free", "starter", "pro", "premium", "enterprise"}) {
            assertEquals(fromSource.get(id), loaded.get(id));
        }
    }

    @Test
    void enforcesDomainAndLanguageLimitsFromTheFile() {
        Plans plans = Plans.fromClasspath();
        int freeDomains = plans.get("free").domains();
        int freeLanguages = plans.get("free").languages();

        plans.requireDomains("free", freeDomains);
        plans.requireLanguages("free", freeLanguages);
        PlanLimitExceededException e = assertThrows(PlanLimitExceededException.class,
                () -> plans.requireDomains("free", freeDomains + 1));
        assertEquals("domains", e.limit());
        assertEquals(freeDomains, e.allowed());
        assertThrows(PlanLimitExceededException.class, () -> plans.requireLanguages("free", freeLanguages + 1));
    }

    @Test
    void nullMeansUnlimited() {
        Plans plans = Plans.fromClasspath();
        assertNull(plans.get("enterprise").domains());
        plans.requireDomains("enterprise", 10_000);
        assertTrue(Plan.within(null, Integer.MAX_VALUE));
    }

    @Test
    void unknownPlanIdIsAConfigurationError() {
        assertThrows(IllegalStateException.class, () -> Plans.fromClasspath().get("gold"));
    }

    @Test
    void missingOrMisspelledLimitFailsFastInsteadOfBecomingUnlimited() {
        assertInvalid(FREE.replace("\"domains\":1,", ""));
        assertInvalid(FREE.replace("\"domains\"", "\"domain\""));
    }

    @Test
    void rejectsDuplicatesNegativesAndUnknownFrequency() {
        assertInvalid(FREE + "," + FREE);
        assertInvalid(FREE.replace("\"prompts\":12", "\"prompts\":-1"));
        assertInvalid(FREE.replace("\"once\"", "\"daily\""));
    }

    private static void assertInvalid(String plansArrayBody) {
        String json = "{\"plans\":[" + plansArrayBody + "]}";
        assertThrows(RuntimeException.class,
                () -> Plans.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }
}
