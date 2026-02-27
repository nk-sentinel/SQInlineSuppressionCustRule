package org.sonar.plugins.inlinesuppression;

import org.junit.jupiter.api.Test;
import org.sonar.api.Plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class InlineSuppressionPluginTest {

    @Test
    void shouldDefineExtensions() {
        Plugin.Context context = mock(Plugin.Context.class);
        when(context.addExtensions(any(Class.class), any(Class.class))).thenReturn(context);

        InlineSuppressionPlugin plugin = new InlineSuppressionPlugin();
        plugin.define(context);

        verify(context).addExtensions(
            InlineSuppressionRulesDefinition.class,
            InlineSuppressionSensor.class
        );
    }
}
