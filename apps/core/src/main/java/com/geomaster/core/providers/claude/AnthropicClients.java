package com.geomaster.core.providers.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import java.time.Duration;

/**
 * One thread-safe client over the official Anthropic Java SDK, shared by the extractor and the
 * Claude engine (scan-engine T9). The key comes from {@code ANTHROPIC_API_KEY} via configuration and
 * is never logged. SDK retries are off: the queues own retries and backoff (REQ-10).
 */
public final class AnthropicClients {

    private AnthropicClients() {}

    public static AnthropicClient create(String apiKey, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(0)
                .build();
    }
}
