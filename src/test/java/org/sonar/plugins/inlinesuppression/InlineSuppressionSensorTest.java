package org.sonar.plugins.inlinesuppression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InlineSuppressionSensorTest {

    @Mock private SensorContext context;
    @Mock private FileSystem fileSystem;
    @Mock private FilePredicates filePredicates;
    @Mock private FilePredicate mainPredicate;
    @Mock private InputFile inputFile;
    @Mock private NewIssue newIssue;
    @Mock private NewIssueLocation newIssueLocation;
    @Mock private TextRange textRange;
    @Mock private SensorDescriptor descriptor;

    private InlineSuppressionSensor sensor;

    @BeforeEach
    void setUp() {
        sensor = new InlineSuppressionSensor();
    }

    @Test
    void shouldDescribeSensor() {
        when(descriptor.name(anyString())).thenReturn(descriptor);

        sensor.describe(descriptor);

        verify(descriptor).name("Inline Suppression Audit Sensor");
    }

    @Test
    void shouldReportIssueForNosonar() throws Exception {
        setupMocks("int x = 1; // NOSONAR", "java");

        sensor.execute(context);

        ArgumentCaptor<RuleKey> ruleKeyCaptor = ArgumentCaptor.forClass(RuleKey.class);
        verify(newIssue).forRule(ruleKeyCaptor.capture());
        assertThat(ruleKeyCaptor.getValue().repository()).isEqualTo("suppression-audit-java");
        assertThat(ruleKeyCaptor.getValue().rule()).isEqualTo("InlineSuppression");
        verify(newIssue).save();
    }

    @Test
    void shouldReportIssueForSuppressWarnings() throws Exception {
        setupMocks("@SuppressWarnings(\"java:S106\")\npublic void foo() {}", "java");

        sensor.execute(context);

        verify(newIssue).save();
        verify(newIssueLocation).message(contains("java:S106"));
    }

    @Test
    void shouldReportIssueForCSharpSuppressMessage() throws Exception {
        setupMocks("[SuppressMessage(\"SonarAnalyzer\", \"S1234\")]\npublic void Foo() {}", "cs");

        sensor.execute(context);

        ArgumentCaptor<RuleKey> ruleKeyCaptor = ArgumentCaptor.forClass(RuleKey.class);
        verify(newIssue).forRule(ruleKeyCaptor.capture());
        assertThat(ruleKeyCaptor.getValue().repository()).isEqualTo("suppression-audit-cs");
        verify(newIssue).save();
    }

    @Test
    void shouldReportMultipleIssuesInSameFile() throws Exception {
        String content = "line1 // NOSONAR\nline2\n@SuppressWarnings(\"java:S106\")\nvoid foo() {}";
        setupMocks(content, "java");

        sensor.execute(context);

        verify(newIssue, times(2)).save();
    }

    @Test
    void shouldNotReportIssuesForCleanFile() throws Exception {
        setupMocks("public class Clean {\n    void foo() {}\n}", "java");

        sensor.execute(context);

        verify(context, never()).newIssue();
    }

    @Test
    void shouldSkipFilesWithUnsupportedLanguage() throws Exception {
        setupMocks("// NOSONAR", "unknown-lang");

        sensor.execute(context);

        verify(context, never()).newIssue();
    }

    @Test
    void shouldSkipFilesWithNullLanguage() throws Exception {
        setupMocks("// NOSONAR", null);

        sensor.execute(context);

        verify(context, never()).newIssue();
    }

    @Test
    void shouldHandleIOExceptionGracefully() throws Exception {
        when(context.fileSystem()).thenReturn(fileSystem);
        when(fileSystem.predicates()).thenReturn(filePredicates);
        when(filePredicates.hasType(InputFile.Type.MAIN)).thenReturn(mainPredicate);
        when(fileSystem.inputFiles(mainPredicate)).thenReturn(List.of(inputFile));
        when(inputFile.language()).thenReturn("java");
        when(inputFile.contents()).thenThrow(new IOException("Read error"));
        when(inputFile.uri()).thenReturn(URI.create("file:///test/Error.java"));

        // Should not throw — logs warning and continues
        sensor.execute(context);

        verify(context, never()).newIssue();
    }

    @Test
    void shouldUseCorrectRuleKeyForPython() throws Exception {
        setupMocks("x = 1  # NOSONAR", "py");

        sensor.execute(context);

        ArgumentCaptor<RuleKey> ruleKeyCaptor = ArgumentCaptor.forClass(RuleKey.class);
        verify(newIssue).forRule(ruleKeyCaptor.capture());
        assertThat(ruleKeyCaptor.getValue().repository()).isEqualTo("suppression-audit-py");
        assertThat(ruleKeyCaptor.getValue().rule()).isEqualTo("InlineSuppression");
    }

    // -----------------------------------------------------------------------
    // Test setup helpers
    // -----------------------------------------------------------------------

    private void setupMocks(String fileContent, String language) throws IOException {
        when(context.fileSystem()).thenReturn(fileSystem);
        when(fileSystem.predicates()).thenReturn(filePredicates);
        when(filePredicates.hasType(InputFile.Type.MAIN)).thenReturn(mainPredicate);
        when(fileSystem.inputFiles(mainPredicate)).thenReturn(List.of(inputFile));
        when(inputFile.language()).thenReturn(language);
        when(inputFile.uri()).thenReturn(URI.create("file:///test/Test.java"));

        if (language != null
                && InlineSuppressionRulesDefinition.SUPPORTED_LANGUAGES.contains(language)) {
            when(inputFile.contents()).thenReturn(fileContent);

            // Only set up issue mocking if the file content has suppressions
            boolean hasSuppression = fileContent.toUpperCase().contains("NOSONAR")
                || fileContent.contains("@SuppressWarnings")
                || fileContent.contains("@Suppress(")
                || fileContent.contains("SuppressMessage");

            if (hasSuppression
                    && SuppressionPatternRegistry.findSuppressions(fileContent).size() > 0) {
                when(inputFile.selectLine(anyInt())).thenReturn(textRange);
                when(context.newIssue()).thenReturn(newIssue);
                when(newIssue.forRule(any(RuleKey.class))).thenReturn(newIssue);
                when(newIssue.newLocation()).thenReturn(newIssueLocation);
                when(newIssueLocation.on(any(InputFile.class))).thenReturn(newIssueLocation);
                when(newIssueLocation.at(any(TextRange.class))).thenReturn(newIssueLocation);
                when(newIssueLocation.message(anyString())).thenReturn(newIssueLocation);
                when(newIssue.at(any(NewIssueLocation.class))).thenReturn(newIssue);
            }
        }
    }

}
