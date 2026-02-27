package org.sonar.plugins.inlinesuppression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class InlineSuppressionRulesDefinitionTest {

    private RulesDefinition.Context context;

    @BeforeEach
    void setUp() {
        context = new RulesDefinition.Context();
        new InlineSuppressionRulesDefinition().define(context);
    }

    @Test
    void shouldCreateRepositoryForEachSupportedLanguage() {
        assertThat(context.repositories())
            .hasSize(InlineSuppressionRulesDefinition.SUPPORTED_LANGUAGES.size());
    }

    @Test
    void shouldCreateCorrectRepositoryKeyForEachLanguage() {
        for (String language : InlineSuppressionRulesDefinition.SUPPORTED_LANGUAGES) {
            RulesDefinition.Repository repo =
                context.repository(InlineSuppressionRulesDefinition.repositoryKey(language));
            assertThat(repo).isNotNull();
            assertThat(repo.language()).isEqualTo(language);
        }
    }

    @Test
    void shouldHaveRuleInEachRepository() {
        for (String language : InlineSuppressionRulesDefinition.SUPPORTED_LANGUAGES) {
            RulesDefinition.Repository repo =
                context.repository(InlineSuppressionRulesDefinition.repositoryKey(language));
            assertThat(repo.rules()).hasSize(1);
            assertThat(repo.rule(InlineSuppressionRulesDefinition.RULE_KEY)).isNotNull();
        }
    }

    @Test
    void shouldDefineRuleAsBlockerVulnerability() {
        RulesDefinition.Repository repo =
            context.repository(InlineSuppressionRulesDefinition.repositoryKey("java"));
        RulesDefinition.Rule rule = repo.rule(InlineSuppressionRulesDefinition.RULE_KEY);

        assertThat(rule).isNotNull();
        assertThat(rule.severity()).isEqualTo("BLOCKER");
        assertThat(rule.type()).isEqualTo(RuleType.VULNERABILITY);
    }

    @Test
    void shouldHaveCorrectRuleName() {
        RulesDefinition.Repository repo =
            context.repository(InlineSuppressionRulesDefinition.repositoryKey("java"));
        RulesDefinition.Rule rule = repo.rule(InlineSuppressionRulesDefinition.RULE_KEY);

        assertThat(rule).isNotNull();
        assertThat(rule.name()).isEqualTo(InlineSuppressionRulesDefinition.RULE_NAME);
    }

    @Test
    void shouldHaveHtmlDescription() {
        RulesDefinition.Repository repo =
            context.repository(InlineSuppressionRulesDefinition.repositoryKey("java"));
        RulesDefinition.Rule rule = repo.rule(InlineSuppressionRulesDefinition.RULE_KEY);

        assertThat(rule).isNotNull();
        assertThat(rule.htmlDescription()).isNotEmpty();
        assertThat(rule.htmlDescription()).contains("NOSONAR");
    }

    @Test
    void shouldHaveTags() {
        RulesDefinition.Repository repo =
            context.repository(InlineSuppressionRulesDefinition.repositoryKey("java"));
        RulesDefinition.Rule rule = repo.rule(InlineSuppressionRulesDefinition.RULE_KEY);

        assertThat(rule).isNotNull();
        assertThat(rule.tags()).contains("policy", "suppression", "audit", "compliance");
    }

    @Test
    void shouldHaveDebtRemediation() {
        RulesDefinition.Repository repo =
            context.repository(InlineSuppressionRulesDefinition.repositoryKey("java"));
        RulesDefinition.Rule rule = repo.rule(InlineSuppressionRulesDefinition.RULE_KEY);

        assertThat(rule).isNotNull();
        assertThat(rule.debtRemediationFunction()).isNotNull();
    }

    @Test
    void shouldGenerateCorrectRepositoryKey() {
        assertThat(InlineSuppressionRulesDefinition.repositoryKey("java"))
            .isEqualTo("suppression-audit-java");
        assertThat(InlineSuppressionRulesDefinition.repositoryKey("cs"))
            .isEqualTo("suppression-audit-cs");
        assertThat(InlineSuppressionRulesDefinition.repositoryKey("py"))
            .isEqualTo("suppression-audit-py");
    }

    @Test
    void shouldGenerateCorrectRepositoryName() {
        assertThat(InlineSuppressionRulesDefinition.repositoryName("java"))
            .isEqualTo("Inline Suppression Audit (java)");
    }
}
