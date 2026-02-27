package org.sonar.plugins.inlinesuppression;

import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines the "Inline Suppression" rule across multiple language-specific repositories.
 *
 * SonarQube requires each rule repository to be bound to exactly one language.
 * To support multi-language detection, we create one repository per supported language,
 * each containing the same rule definition.
 */
public class InlineSuppressionRulesDefinition implements RulesDefinition {

    public static final String RULE_KEY = "InlineSuppression";
    public static final String RULE_NAME = "Inline suppression of static analysis findings is not allowed";

    /**
     * All languages supported by this plugin.
     * The sensor will only process files whose language is in this list.
     */
    static final List<String> SUPPORTED_LANGUAGES = List.of(
        "java",    // Java
        "cs",      // C#
        "py",      // Python
        "js",      // JavaScript
        "ts",      // TypeScript
        "kotlin",  // Kotlin
        "go",      // Go
        "php",     // PHP
        "ruby",    // Ruby
        "scala",   // Scala
        "vbnet",   // VB.NET
        "xml",     // XML
        "css",     // CSS
        "web",     // HTML
        "c",       // C
        "cpp"      // C++
    );

    private static final String REPOSITORY_PREFIX = "suppression-audit-";

    private static final String RULE_DESCRIPTION_PATH =
        "/org/sonar/plugins/inlinesuppression/rules/InlineSuppression.html";

    public static String repositoryKey(String language) {
        return REPOSITORY_PREFIX + language;
    }

    public static String repositoryName(String language) {
        return "Inline Suppression Audit (" + language + ")";
    }

    @Override
    public void define(Context context) {
        for (String language : SUPPORTED_LANGUAGES) {
            NewRepository repository = context.createRepository(repositoryKey(language), language)
                .setName(repositoryName(language));

            createRule(repository);
            repository.done();
        }
    }

    private void createRule(NewRepository repository) {
        NewRule rule = repository.createRule(RULE_KEY)
            .setName(RULE_NAME)
            .setHtmlDescription(loadRuleDescription())
            .setSeverity("BLOCKER")
            .setType(RuleType.VULNERABILITY)
            .setTags("security", "suppression", "audit", "compliance");

        rule.setDebtRemediationFunction(
            rule.debtRemediationFunctions().constantPerIssue("30min"));
    }

    private String loadRuleDescription() {
        try (InputStream is = getClass().getResourceAsStream(RULE_DESCRIPTION_PATH)) {
            if (is == null) {
                return getDefaultDescription();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return getDefaultDescription();
        }
    }

    private static String getDefaultDescription() {
        return "<p>Inline suppression of static analysis findings is not allowed. "
            + "Remove suppression comments, annotations, and attributes to ensure all "
            + "code is scanned by SonarQube.</p>";
    }
}
