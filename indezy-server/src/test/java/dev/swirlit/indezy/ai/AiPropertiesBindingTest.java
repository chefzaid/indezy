package dev.swirlit.indezy.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesBindingTest {

    @Test
    void bindsOneTasksModelFromTheEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        // Named as the process environment is, so names map as they do in a deployment.
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
            "INDEZY_AI_API_KEY", "sk-ant-x", "INDEZY_AI_MODELS_CHAT", "claude-sonnet-5-5")));
        AiProperties properties = new Binder(ConfigurationPropertySources.get(environment)).bind("indezy.ai", AiProperties.class).get();

        assertThat(properties.model(AiFeature.CHAT)).isEqualTo("claude-sonnet-5-5");
        assertThat(properties.model(AiFeature.FIT)).isEqualTo("claude-opus-5-5");
        assertThat(properties.model(AiFeature.POSTING)).isEqualTo("claude-haiku-5-5");
        assertThat(properties.configured()).isTrue();
    }
}
