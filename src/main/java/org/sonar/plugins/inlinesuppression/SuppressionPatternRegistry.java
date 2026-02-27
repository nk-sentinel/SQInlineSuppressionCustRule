package org.sonar.plugins.inlinesuppression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry of all suppression patterns detected by this plugin.
 * Scans source file content for NOSONAR comments, @SuppressWarnings annotations,
 * @Suppress (Kotlin) annotations, and [SuppressMessage] (C#) attributes.
 *
 * <h3>Detected patterns:</h3>
 * <ul>
 *   <li>NOSONAR — line-level comment suppression (all languages)</li>
 *   <li>@SuppressWarnings — annotation-based suppression (Java, Kotlin) when targeting SonarQube rules or "all"</li>
 *   <li>@Suppress — Kotlin-specific annotation when targeting SonarQube rules or "all"</li>
 *   <li>[SuppressMessage] — C# attribute when targeting SonarQube rules or Sonar-related categories</li>
 * </ul>
 *
 * <h3>Future improvements:</h3>
 * <ul>
 *   <li>#pragma warning disable (C#)</li>
 *   <li>@Generated / @javax.annotation.Generated / @jakarta.annotation.Generated (Java)</li>
 *   <li>[GeneratedCode] / [GeneratedCodeAttribute] (C#)</li>
 * </ul>
 */
public final class SuppressionPatternRegistry {

    private SuppressionPatternRegistry() {
        // utility class
    }

    public enum SuppressionType {
        NOSONAR,
        SUPPRESS_WARNINGS,
        KOTLIN_SUPPRESS,
        SUPPRESS_MESSAGE
    }

    /**
     * Represents a detected suppression pattern in a source file.
     *
     * @param lineNumber 1-based line number where the suppression was found
     * @param type       the kind of suppression detected
     * @param message    human-readable message for the SonarQube issue
     */
    public record SuppressionMatch(int lineNumber, SuppressionType type, String message) {}

    // ---------------------------------------------------------------------------
    // Patterns
    // ---------------------------------------------------------------------------

    /** NOSONAR comment — case insensitive, matches in any comment style (// # /* <!-- --) */
    static final Pattern NOSONAR_PATTERN =
        Pattern.compile("\\bNOSONAR\\b", Pattern.CASE_INSENSITIVE);

    /** @SuppressWarnings annotation — captures the annotation value content */
    static final Pattern SUPPRESS_WARNINGS_PATTERN =
        Pattern.compile("@SuppressWarnings\\s*\\(([\\s\\S]*?)\\)");

    /**
     * @Suppress annotation (Kotlin) — negative lookahead prevents matching
     * @SuppressWarnings or @SuppressLint (Android).
     */
    static final Pattern KOTLIN_SUPPRESS_PATTERN =
        Pattern.compile("@Suppress(?!Warnings|Lint)\\s*\\(([\\s\\S]*?)\\)");

    /**
     * [SuppressMessage] attribute (C#) — handles optional attribute targets
     * (assembly:, module:, etc.) and fully-qualified name.
     */
    static final Pattern SUPPRESS_MESSAGE_PATTERN =
        Pattern.compile(
            "\\[\\s*(?:(?:assembly|module|type|method|return|param)\\s*:\\s*)?"
            + "(?:System\\.Diagnostics\\.CodeAnalysis\\.)?SuppressMessage\\s*\\("
            + "([\\s\\S]*?)\\)\\s*\\]");

    /**
     * SonarQube rule reference with a language prefix.
     * Covers all known SonarQube language rule-key prefixes.
     */
    static final Pattern SQ_RULE_WITH_PREFIX =
        Pattern.compile(
            "(?:java|squid|csharpsquid|javascript|typescript|python|kotlin"
            + "|php|ruby|go|scala|vbnet|xml|css|web|plsql|tsql"
            + "|c|cpp|objc|swift|abap|cobol|flex)\\s*:\\s*S\\d{3,}");

    /** Bare SonarQube rule reference — S followed by 3+ digits (e.g. S106, S1234) */
    static final Pattern BARE_SQ_RULE =
        Pattern.compile("\\bS\\d{3,}\\b");

    /** "all" literal inside a quoted string — suppresses ALL rules */
    static final Pattern ALL_SUPPRESS =
        Pattern.compile("\"all\"", Pattern.CASE_INSENSITIVE);

    /** Sonar-related category string inside [SuppressMessage] */
    static final Pattern SONAR_CATEGORY =
        Pattern.compile("(?i)\\bsonar\\b");

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Scans the given file content for all suppression patterns.
     *
     * @param content full source file content
     * @return list of detected suppressions (may be empty, never null)
     */
    public static List<SuppressionMatch> findSuppressions(String content) {
        List<SuppressionMatch> matches = new ArrayList<>();
        Set<Integer> reportedLines = new HashSet<>();

        // 1. NOSONAR — line-by-line scan
        findNosonarMatches(content, reportedLines, matches);

        // 2. @SuppressWarnings — multiline regex
        findAnnotationMatches(content, SUPPRESS_WARNINGS_PATTERN,
            SuppressionType.SUPPRESS_WARNINGS, "@SuppressWarnings", reportedLines, matches);

        // 3. @Suppress (Kotlin) — multiline regex
        findAnnotationMatches(content, KOTLIN_SUPPRESS_PATTERN,
            SuppressionType.KOTLIN_SUPPRESS, "@Suppress", reportedLines, matches);

        // 4. [SuppressMessage] (C#) — multiline regex
        findSuppressMessageMatches(content, reportedLines, matches);

        return matches;
    }

    // ---------------------------------------------------------------------------
    // Internal detection methods
    // ---------------------------------------------------------------------------

    private static void findNosonarMatches(String content, Set<Integer> reportedLines,
                                           List<SuppressionMatch> matches) {
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (NOSONAR_PATTERN.matcher(lines[i]).find()) {
                int lineNum = i + 1;
                if (reportedLines.add(lineNum)) {
                    matches.add(new SuppressionMatch(lineNum, SuppressionType.NOSONAR,
                        "Remove this use of \"NOSONAR\". "
                        + "Suppressing SonarQube issues inline is not permitted."));
                }
            }
        }
    }

    private static void findAnnotationMatches(String content, Pattern pattern,
                                               SuppressionType type, String annotationName,
                                               Set<Integer> reportedLines,
                                               List<SuppressionMatch> matches) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String annotationContent = matcher.group(1);
            List<String> ruleRefs = extractRuleReferences(annotationContent);
            boolean hasAllSuppress = ALL_SUPPRESS.matcher(annotationContent).find();

            if (!ruleRefs.isEmpty() || hasAllSuppress) {
                int lineNum = getLineNumber(content, matcher.start());
                if (reportedLines.add(lineNum)) {
                    String message = buildAnnotationMessage(annotationName, ruleRefs, hasAllSuppress);
                    matches.add(new SuppressionMatch(lineNum, type, message));
                }
            }
        }
    }

    private static void findSuppressMessageMatches(String content, Set<Integer> reportedLines,
                                                    List<SuppressionMatch> matches) {
        Matcher matcher = SUPPRESS_MESSAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            String attrContent = matcher.group(1);
            List<String> ruleRefs = extractRuleReferences(attrContent);
            boolean hasSonarCategory = SONAR_CATEGORY.matcher(attrContent).find();

            if (!ruleRefs.isEmpty() || hasSonarCategory) {
                int lineNum = getLineNumber(content, matcher.start());
                if (reportedLines.add(lineNum)) {
                    String detail = !ruleRefs.isEmpty()
                        ? "rule(s): " + String.join(", ", ruleRefs)
                        : "SonarQube rules";
                    String message = String.format(
                        "Remove this [SuppressMessage] attribute suppressing %s. "
                        + "Suppressing SonarQube rules is not permitted.", detail);
                    matches.add(new SuppressionMatch(lineNum, SuppressionType.SUPPRESS_MESSAGE,
                        message));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private static String buildAnnotationMessage(String annotationName,
                                                  List<String> ruleRefs,
                                                  boolean hasAllSuppress) {
        if (hasAllSuppress) {
            return String.format(
                "Remove this %s(\"all\") annotation. "
                + "Blanket suppression of all warnings is not permitted.", annotationName);
        }
        return String.format(
            "Remove this %s annotation suppressing rule(s): %s. "
            + "Suppressing SonarQube rules is not permitted.",
            annotationName, String.join(", ", ruleRefs));
    }

    /**
     * Extracts SonarQube rule references from annotation/attribute content.
     * Tries language-prefixed rules first (e.g. java:S106), falls back to bare rules (e.g. S1234).
     */
    static List<String> extractRuleReferences(String content) {
        List<String> refs = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Prefer prefixed rules (java:S106, squid:S1166, typescript:S3776, etc.)
        Matcher prefixMatcher = SQ_RULE_WITH_PREFIX.matcher(content);
        while (prefixMatcher.find()) {
            String ref = prefixMatcher.group().replaceAll("\\s+", "");
            if (seen.add(ref)) {
                refs.add(ref);
            }
        }

        // Fall back to bare rules only when no prefixed rules found
        if (refs.isEmpty()) {
            Matcher bareMatcher = BARE_SQ_RULE.matcher(content);
            while (bareMatcher.find()) {
                String ref = bareMatcher.group();
                if (seen.add(ref)) {
                    refs.add(ref);
                }
            }
        }

        return refs;
    }

    /**
     * Calculates the 1-based line number for a given character offset in the content.
     */
    static int getLineNumber(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
