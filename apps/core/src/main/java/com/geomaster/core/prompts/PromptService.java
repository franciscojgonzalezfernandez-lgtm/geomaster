package com.geomaster.core.prompts;

import com.geomaster.core.accounts.AccountRepository;
import com.geomaster.core.accounts.ProjectService;
import com.geomaster.core.api.InvalidInputException;
import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.plans.Plans;
import com.geomaster.core.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Hand-entered prompts within the plan's `prompts` limit (scan-engine REQ-1, REQ-2). */
@Service
public class PromptService {

    /** The six intent topics of FR-ONB-03. */
    static final Set<String> TOPICS = Set.of("reviews", "pricing", "use_cases", "alternatives", "how_to", "comparison");
    static final int MAX_TEXT = 2000;

    private final PromptRepository prompts;
    private final ProjectService projects;
    private final AccountRepository accounts;
    private final Plans plans;

    public PromptService(PromptRepository prompts, ProjectService projects, AccountRepository accounts, Plans plans) {
        this.prompts = prompts;
        this.projects = projects;
        this.accounts = accounts;
        this.plans = plans;
    }

    @Transactional
    public Prompt create(UUID projectId, String rawLocale, String rawTopic, String rawText) {
        ProjectService.ProjectView project = projects.get(projectId); // membership + 404 (REQ-7)
        String locale = rawLocale == null ? "" : rawLocale.trim().toLowerCase(Locale.ROOT);
        if (!project.languages().contains(locale)) {
            throw new InvalidInputException("locale", "locale must be one of the project's languages " + project.languages());
        }
        String topic = topic(rawTopic);
        String text = text(rawText);
        // Lock the account so two concurrent adds cannot overshoot the limit together.
        var account = accounts.lockForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account"));
        plans.requirePrompts(account.planId(), prompts.countActiveForAccount() + 1);
        return prompts.create(projectId, locale, topic, text);
    }

    public List<Prompt> list(UUID projectId) {
        projects.get(projectId);
        return prompts.listActive(projectId);
    }

    @Transactional
    public Prompt update(UUID projectId, UUID promptId, String rawTopic, String rawText) {
        projects.get(projectId);
        Prompt current = prompts.lock(projectId, promptId).orElseThrow(() -> new NotFoundException("prompt"));
        if (rawText != null) {
            String text = text(rawText);
            if (!text.equals(current.text())) {
                prompts.newVersion(promptId, current.currentVersion() + 1, text);
            }
        }
        if (rawTopic != null) {
            prompts.setTopic(promptId, topic(rawTopic));
        }
        return prompts.find(projectId, promptId).orElseThrow();
    }

    @Transactional
    public void delete(UUID projectId, UUID promptId) {
        projects.get(projectId);
        prompts.lock(projectId, promptId).orElseThrow(() -> new NotFoundException("prompt"));
        prompts.softDelete(promptId);
    }

    private static String topic(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!TOPICS.contains(v)) {
            throw new InvalidInputException("topic", "topic must be one of " + TOPICS);
        }
        return v;
    }

    private static String text(String raw) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty() || v.length() > MAX_TEXT) {
            throw new InvalidInputException("text", "text must be 1.." + MAX_TEXT + " characters");
        }
        return v;
    }
}
