package com.geomaster.core.providers;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Real cost of one call in CHF from configured prices (REQ-14, NFR-04): tokens × per-million price
 * + searches × per-thousand price, converted from USD. Rounded to 6 decimals (matches
 * {@code numeric(12,6)}).
 */
public final class CostCalculator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);

    private final ProvidersProperties props;

    public CostCalculator(ProvidersProperties props) {
        this.props = props;
    }

    public record Cost(BigDecimal chf, String priceVersion) {}

    public Cost cost(Engine engine, ProviderAnswer answer) {
        ProvidersProperties.Prices p = props.engine(engine).prices();
        if (p == null) {
            throw new IllegalStateException("No prices configured for engine " + engine.id());
        }
        BigDecimal usd = BigDecimal.valueOf(answer.usage().inputTokens()).multiply(p.inputPerMillionUsd())
                .divide(MILLION, MathContext.DECIMAL64)
                .add(BigDecimal.valueOf(answer.usage().outputTokens()).multiply(p.outputPerMillionUsd())
                        .divide(MILLION, MathContext.DECIMAL64))
                .add(BigDecimal.valueOf(answer.searchCount()).multiply(p.perThousandSearchesUsd())
                        .divide(THOUSAND, MathContext.DECIMAL64));
        return new Cost(usd.multiply(props.usdToChf()).setScale(6, RoundingMode.HALF_UP), p.version());
    }
}
