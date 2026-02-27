package org.sonar.plugins.inlinesuppression;

import org.sonar.api.Plugin;

/**
 * Entry point for the Inline Suppression Audit plugin.
 * Registers the rules definition and sensor with the SonarQube platform.
 */
public class InlineSuppressionPlugin implements Plugin {

    @Override
    public void define(Context context) {
        context.addExtensions(
            InlineSuppressionRulesDefinition.class,
            InlineSuppressionSensor.class
        );
    }
}
