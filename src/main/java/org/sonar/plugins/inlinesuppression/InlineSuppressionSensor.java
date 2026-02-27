package org.sonar.plugins.inlinesuppression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;

import java.io.IOException;
import java.util.List;

/**
 * Sensor that scans all source files for inline suppression patterns.
 * Reports a Blocker/Vulnerability issue for each detected suppression.
 *
 * <p>This sensor runs on ALL languages supported by
 * {@link InlineSuppressionRulesDefinition#SUPPORTED_LANGUAGES}.
 * For each file, it determines the language and reports issues against
 * the matching language-specific rule repository.</p>
 */
public class InlineSuppressionSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(InlineSuppressionSensor.class);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.name("Inline Suppression Audit Sensor");
    }

    @Override
    public void execute(SensorContext context) {
        FileSystem fs = context.fileSystem();
        Iterable<InputFile> files = fs.inputFiles(
            fs.predicates().hasType(InputFile.Type.MAIN)
        );

        for (InputFile file : files) {
            String language = file.language();
            if (language == null
                    || !InlineSuppressionRulesDefinition.SUPPORTED_LANGUAGES.contains(language)) {
                continue;
            }
            scanFile(context, file, language);
        }
    }

    private void scanFile(SensorContext context, InputFile file, String language) {
        String content;
        try {
            content = file.contents();
        } catch (IOException e) {
            LOG.warn("Unable to read file {}, skipping suppression audit", file.uri(), e);
            return;
        }

        List<SuppressionPatternRegistry.SuppressionMatch> matches =
            SuppressionPatternRegistry.findSuppressions(content);

        if (matches.isEmpty()) {
            return;
        }

        RuleKey ruleKey = RuleKey.of(
            InlineSuppressionRulesDefinition.repositoryKey(language),
            InlineSuppressionRulesDefinition.RULE_KEY
        );

        for (SuppressionPatternRegistry.SuppressionMatch match : matches) {
            NewIssue issue = context.newIssue().forRule(ruleKey);
            NewIssueLocation location = issue.newLocation()
                .on(file)
                .at(file.selectLine(match.lineNumber()))
                .message(match.message());
            issue.at(location);
            issue.save();
        }

        LOG.debug("Found {} suppression(s) in {}", matches.size(), file.uri());
    }
}
