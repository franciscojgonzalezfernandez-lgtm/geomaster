package com.geomaster.core.scan;

import com.geomaster.core.providers.Engine;
import com.geomaster.core.providers.ProviderAnswer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Provider calls = the shared response cache (FR-SCN-04, FR-SCN-06). Not tenant-owned. */
@Repository
public class LlmCallRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient db;

    public LlmCallRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<UUID> findByCacheKey(String cacheKey) {
        return db.sql("SELECT id FROM llm_call WHERE cache_key = :k").param("k", cacheKey).query(UUID.class).optional();
    }

    /**
     * Store a call and its citations. If another worker stored the same key first, keep theirs and
     * return its id (the caller still accounts for the cost it actually paid).
     */
    @Transactional
    public UUID insert(String cacheKey, Engine engine, String locale, String country, String region,
                       String promptText, ProviderAnswer a, BigDecimal costChf, String priceVersion) {
        Optional<UUID> id = db.sql("""
                INSERT INTO llm_call (cache_key, engine, model, model_version, locale, country, region, prompt_sha256,
                                      answer_text, search_queries, search_count, input_tokens, output_tokens,
                                      cost_chf, price_version, search_suggestions_html, latency_ms)
                VALUES (:key, :engine, :model, :modelVersion, :locale, :country, :region, :sha,
                        :text, CAST(:queries AS jsonb), :searches, :in, :out,
                        :cost, :priceVersion, :suggestions, :latency)
                ON CONFLICT (cache_key) DO NOTHING
                RETURNING id
                """)
                .param("key", cacheKey).param("engine", engine.id()).param("model", a.model())
                .param("modelVersion", a.modelVersion()).param("locale", locale).param("country", country)
                .param("region", region).param("sha", CacheKey.sha256(promptText)).param("text", a.text())
                .param("queries", JSON.writeValueAsString(a.searchQueries())).param("searches", a.searchCount())
                .param("in", a.usage().inputTokens()).param("out", a.usage().outputTokens())
                .param("cost", costChf).param("priceVersion", priceVersion)
                .param("suggestions", a.searchSuggestionsHtml()).param("latency", a.latency().toMillis())
                .query(UUID.class).optional();
        if (id.isEmpty()) {
            return findByCacheKey(cacheKey).orElseThrow();
        }
        int position = 1;
        for (ProviderAnswer.Citation c : a.citations()) {
            db.sql("""
                    INSERT INTO llm_call_citation (llm_call_id, position, url, title, domain)
                    VALUES (:call, :pos, :url, :title, :domain)
                    """)
                    .param("call", id.get()).param("pos", position++).param("url", c.url())
                    .param("title", c.title()).param("domain", domainOf(c.url()))
                    .update();
        }
        return id.get();
    }

    static String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
