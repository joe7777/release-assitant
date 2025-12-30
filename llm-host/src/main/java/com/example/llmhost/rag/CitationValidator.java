package com.example.llmhost.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.llmhost.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CitationValidator {

    private static final Pattern SOURCE_CITATION_PATTERN = Pattern.compile("\\[(S\\d+)]");

    private final AppProperties appProperties;

    public CitationValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public CitationValidationResult validate(String answer, int providedSources) {
        List<String> citationsFound = extractCitationsFound(answer);
        List<String> missingSources = computeMissingSources(providedSources, citationsFound);
        double coverageRatio = providedSources == 0
                ? 1.0
                : ((double) citationsFound.size() / (double) providedSources);
        return new CitationValidationResult(citationsFound, missingSources, coverageRatio, providedSources);
    }

    public RetryDirective evaluateRetry(CitationValidationResult result) {
        if (result.providedSources() <= 0) {
            return RetryDirective.none();
        }
        if (result.citationsFound().isEmpty()) {
            return new RetryDirective(true, RetryReason.NO_CITATIONS);
        }
        AppProperties.RagProperties rag = appProperties.getRag();
        if (result.providedSources() >= rag.getCitationMinSourcesForCoverage()
                && result.coverageRatio() < rag.getCitationCoverageRatio()) {
            return new RetryDirective(true, RetryReason.LOW_COVERAGE);
        }
        return RetryDirective.none();
    }

    public List<String> extractCitationsFound(String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = SOURCE_CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return List.copyOf(found);
    }

    private List<String> computeMissingSources(int providedSources, List<String> found) {
        if (providedSources <= 0) {
            return List.of();
        }
        Set<String> expected = new LinkedHashSet<>();
        for (int i = 0; i < providedSources; i++) {
            expected.add("S" + (i + 1));
        }
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(found);
        return List.copyOf(missing);
    }

    public record CitationValidationResult(List<String> citationsFound, List<String> missingSources,
                                           double coverageRatio, int providedSources) {
    }

    public record RetryDirective(boolean retry, RetryReason reason) {
        static RetryDirective none() {
            return new RetryDirective(false, RetryReason.NONE);
        }
    }

    public enum RetryReason {
        NONE,
        NO_CITATIONS,
        LOW_COVERAGE
    }
}
