package org.sonar.plugins.inlinesuppression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.plugins.inlinesuppression.SuppressionPatternRegistry.SuppressionType;

class SuppressionPatternRegistryTest {

    // -----------------------------------------------------------------------
    // NOSONAR detection
    // -----------------------------------------------------------------------

    @Test
    void shouldDetectNosonarComment() {
        String content = "int x = 1; // NOSONAR";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.NOSONAR);
        assertThat(matches.get(0).lineNumber()).isEqualTo(1);
        assertThat(matches.get(0).message()).contains("NOSONAR");
    }

    @Test
    void shouldDetectNosonarCaseInsensitive() {
        String content = "int x = 1; // nosonar\nint y = 2; // Nosonar\nint z = 3; // NOSONAR";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "x = 1; // NOSONAR",
        "x = 1; /* NOSONAR */",
        "x = 1 # NOSONAR",
        "<!-- NOSONAR -->",
        "-- NOSONAR",
        "' NOSONAR"
    })
    void shouldDetectNosonarInDifferentCommentStyles(String line) {
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(line);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.NOSONAR);
    }

    @Test
    void shouldDetectNosonarWithExplanation() {
        String content = "int x = 1; // NOSONAR: this is intentional";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldDetectMultipleNosonarOnDifferentLines() {
        String content = "line1 // NOSONAR\nline2\nline3 // NOSONAR\nline4";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).lineNumber()).isEqualTo(1);
        assertThat(matches.get(1).lineNumber()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // @SuppressWarnings detection
    // -----------------------------------------------------------------------

    @Test
    void shouldDetectSuppressWarningsWithSingleJavaRule() {
        String content = "@SuppressWarnings(\"java:S106\")\npublic void foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.SUPPRESS_WARNINGS);
        assertThat(matches.get(0).lineNumber()).isEqualTo(1);
        assertThat(matches.get(0).message()).contains("java:S106");
    }

    @Test
    void shouldDetectSuppressWarningsWithMultipleRules() {
        String content = "@SuppressWarnings({\"java:S106\", \"java:S1186\"})\npublic void foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message()).contains("java:S106").contains("java:S1186");
    }

    @Test
    void shouldDetectSuppressWarningsAll() {
        String content = "@SuppressWarnings(\"all\")\npublic class MyClass {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message()).contains("all");
    }

    @Test
    void shouldDetectSuppressWarningsWithOldSquidPrefix() {
        String content = "@SuppressWarnings(\"squid:S1166\")\npublic void foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message()).contains("squid:S1166");
    }

    @Test
    void shouldDetectSuppressWarningsWithValueAttribute() {
        String content = "@SuppressWarnings(value = \"java:S106\")\npublic void foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldDetectMultilineSuppressWarnings() {
        String content = """
            @SuppressWarnings({
                "java:S106",
                "java:S1186"
            })
            public void foo() {}""";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message()).contains("java:S106").contains("java:S1186");
    }

    @Test
    void shouldNotDetectSuppressWarningsForNonSonarRules() {
        String content = "@SuppressWarnings(\"unchecked\")\npublic void foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).isEmpty();
    }

    @Test
    void shouldNotFlagDeprecationOrSerialSuppression() {
        String content = """
            @SuppressWarnings("deprecation")
            public void foo() {}

            @SuppressWarnings("serial")
            public class MyClass implements Serializable {}""";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).isEmpty();
    }

    @Test
    void shouldDetectMixedSonarAndCompilerSuppression() {
        String content = "@SuppressWarnings({\"unchecked\", \"java:S106\"})\npublic void foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message()).contains("java:S106");
    }

    // -----------------------------------------------------------------------
    // Multi-language rule prefix detection
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "@SuppressWarnings(\"csharpsquid:S1234\")",
        "@SuppressWarnings(\"javascript:S1234\")",
        "@SuppressWarnings(\"typescript:S3776\")",
        "@SuppressWarnings(\"python:S1234\")",
        "@SuppressWarnings(\"kotlin:S1234\")",
        "@SuppressWarnings(\"go:S1234\")",
        "@SuppressWarnings(\"php:S1234\")",
        "@SuppressWarnings(\"ruby:S1234\")",
        "@SuppressWarnings(\"scala:S1234\")",
        "@SuppressWarnings(\"vbnet:S1234\")",
        "@SuppressWarnings(\"c:S1234\")",
        "@SuppressWarnings(\"cpp:S1234\")",
        "@SuppressWarnings(\"plsql:S1234\")",
        "@SuppressWarnings(\"tsql:S1234\")",
        "@SuppressWarnings(\"xml:S1234\")",
        "@SuppressWarnings(\"css:S1234\")",
        "@SuppressWarnings(\"web:S1234\")",
        "@SuppressWarnings(\"swift:S1234\")",
        "@SuppressWarnings(\"objc:S1234\")",
        "@SuppressWarnings(\"abap:S1234\")",
        "@SuppressWarnings(\"cobol:S1234\")",
        "@SuppressWarnings(\"flex:S1234\")"
    })
    void shouldDetectAllLanguageRulePrefixes(String annotation) {
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(annotation);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.SUPPRESS_WARNINGS);
    }

    // -----------------------------------------------------------------------
    // @Suppress (Kotlin) detection
    // -----------------------------------------------------------------------

    @Test
    void shouldDetectKotlinSuppressWithSonarRule() {
        String content = "@Suppress(\"kotlin:S1234\")\nfun process() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.KOTLIN_SUPPRESS);
        assertThat(matches.get(0).message()).contains("kotlin:S1234");
    }

    @Test
    void shouldDetectKotlinSuppressWithBareRuleId() {
        String content = "@Suppress(\"S1234\")\nfun process() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.KOTLIN_SUPPRESS);
    }

    @Test
    void shouldDetectKotlinSuppressAll() {
        String content = "@Suppress(\"all\")\nfun process() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message()).contains("all");
    }

    @Test
    void shouldNotDetectKotlinSuppressForNonSonarWarnings() {
        String content = "@Suppress(\"UNUSED_PARAMETER\")\nfun process(x: Int) {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).isEmpty();
    }

    @Test
    void shouldNotConfuseKotlinSuppressWithSuppressWarnings() {
        // @SuppressWarnings should be detected as SUPPRESS_WARNINGS, not KOTLIN_SUPPRESS
        String content = "@SuppressWarnings(\"java:S106\")\npublic void foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.SUPPRESS_WARNINGS);
    }

    // -----------------------------------------------------------------------
    // [SuppressMessage] (C#) detection
    // -----------------------------------------------------------------------

    @Test
    void shouldDetectSuppressMessageWithSonarCategory() {
        String content = "[SuppressMessage(\"SonarAnalyzer\", \"S1234\")]\npublic void Foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.SUPPRESS_MESSAGE);
    }

    @Test
    void shouldDetectSuppressMessageWithSonarQubeCategory() {
        String content = "[SuppressMessage(\"SonarQube\", \"S1234\")]\npublic void Foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldDetectFullyQualifiedSuppressMessage() {
        String content = "[System.Diagnostics.CodeAnalysis.SuppressMessage(\"SonarAnalyzer\", \"S1234\")]\npublic void Foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldDetectAssemblyLevelSuppressMessage() {
        String content = "[assembly: SuppressMessage(\"SonarAnalyzer\", \"S1234\")]\n";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldDetectSuppressMessageWithJustification() {
        String content = "[SuppressMessage(\"SonarAnalyzer\", \"S1234\", Justification = \"By design\")]\npublic void Foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
    }

    @Test
    void shouldDetectSuppressMessageWithBareRuleId() {
        String content = "[SuppressMessage(\"Category\", \"S1234\")]\npublic void Foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message()).contains("S1234");
    }

    @Test
    void shouldNotDetectSuppressMessageForNonSonarCategory() {
        String content = "[SuppressMessage(\"Microsoft.Design\", \"CA1062\")]\npublic void Foo() {}";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Clean files (no suppressions)
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnEmptyForCleanJavaFile() {
        String content = """
            public class Clean {
                public void process() {
                    System.out.println("hello");
                }
            }""";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyContent() {
        assertThat(SuppressionPatternRegistry.findSuppressions("")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Deduplication
    // -----------------------------------------------------------------------

    @Test
    void shouldNotReportDuplicatesOnSameLine() {
        // If both NOSONAR and @SuppressWarnings are on the same line
        String content = "@SuppressWarnings(\"java:S106\") // NOSONAR";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        // NOSONAR is detected first, @SuppressWarnings on same line is deduplicated
        assertThat(matches).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Helper method tests
    // -----------------------------------------------------------------------

    @Test
    void shouldExtractPrefixedRuleReferences() {
        List<String> refs = SuppressionPatternRegistry.extractRuleReferences(
            "\"java:S106\", \"java:S1186\"");

        assertThat(refs).containsExactly("java:S106", "java:S1186");
    }

    @Test
    void shouldExtractBareRuleReferences() {
        List<String> refs = SuppressionPatternRegistry.extractRuleReferences("\"S1234\"");

        assertThat(refs).containsExactly("S1234");
    }

    @Test
    void shouldPreferPrefixedOverBareReferences() {
        List<String> refs = SuppressionPatternRegistry.extractRuleReferences(
            "\"java:S1234\"");

        // Prefixed is found, so bare search is skipped
        assertThat(refs).containsExactly("java:S1234");
    }

    @Test
    void shouldDeduplicateRuleReferences() {
        List<String> refs = SuppressionPatternRegistry.extractRuleReferences(
            "\"java:S106\", \"java:S106\"");

        assertThat(refs).containsExactly("java:S106");
    }

    @Test
    void shouldReturnEmptyForNoRuleReferences() {
        List<String> refs = SuppressionPatternRegistry.extractRuleReferences("\"unchecked\"");

        assertThat(refs).isEmpty();
    }

    @Test
    void shouldCalculateLineNumberCorrectly() {
        String content = "line1\nline2\nline3\nline4";

        assertThat(SuppressionPatternRegistry.getLineNumber(content, 0)).isEqualTo(1);
        assertThat(SuppressionPatternRegistry.getLineNumber(content, 6)).isEqualTo(2);
        assertThat(SuppressionPatternRegistry.getLineNumber(content, 12)).isEqualTo(3);
        assertThat(SuppressionPatternRegistry.getLineNumber(content, 18)).isEqualTo(4);
    }

    @Test
    void shouldHandleMultipleSuppressionsInSameFile() {
        String content = """
            int x = 1; // NOSONAR
            int y = 2;
            @SuppressWarnings("java:S106")
            public void foo() {
                int z = 3; // NOSONAR
            }""";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).type()).isEqualTo(SuppressionType.NOSONAR);
        assertThat(matches.get(1).type()).isEqualTo(SuppressionType.NOSONAR);
        assertThat(matches.get(2).type()).isEqualTo(SuppressionType.SUPPRESS_WARNINGS);
    }

    @Test
    void shouldDetectMultipleRulesAcrossLanguages() {
        String content = "@SuppressWarnings({\"java:S106\", \"kotlin:S1186\", \"typescript:S3776\"})";
        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).message())
            .contains("java:S106")
            .contains("kotlin:S1186")
            .contains("typescript:S3776");
    }
}
