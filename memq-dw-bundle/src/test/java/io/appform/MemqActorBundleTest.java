package io.appform;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.appform.config.ExecutorConfig;
import io.appform.config.MemqConfig;
import io.dropwizard.Configuration;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.AdminEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class MemqActorBundleTest {

    private static final String GLOBAL_THREADPOOL_NAME = "GLOBAL";
    final TestConfig testConfig = new TestConfig();
    HealthCheckRegistry healthChecks = mock(HealthCheckRegistry.class);
    JerseyEnvironment jerseyEnvironment = mock(JerseyEnvironment.class);
    LifecycleEnvironment lifecycleEnvironment = mock(LifecycleEnvironment.class);
    Environment environment = mock(Environment.class);
    AdminEnvironment adminEnvironment = mock(AdminEnvironment.class);
    Bootstrap<?> bootstrap = mock(Bootstrap.class);

    @Test
    void testBundle() {
        assertDoesNotThrow(() -> {
            when(environment.jersey()).thenReturn(jerseyEnvironment);
            when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
            when(environment.healthChecks()).thenReturn(healthChecks);
            when(environment.admin()).thenReturn(adminEnvironment);
            when(jerseyEnvironment.getResourceConfig()).thenReturn(new DropwizardResourceConfig());
            when(bootstrap.getHealthCheckRegistry()).thenReturn(healthChecks);
            MemqActorBundle<TestConfig> bundle = new MemqActorBundle<>() {

                @Override
                protected MemqConfig config(TestConfig testConfig) {
                    return testConfig.getMemqConfig();
                }

                @Override
                protected ExecutorServiceProvider executorServiceProvider(TestConfig testConfig) {
                    return (name, parallel, queueSize) -> Executors.newFixedThreadPool(Constants.DEFAULT_THREADPOOL);
                }
            };
            bundle.initialize(bootstrap);
            bundle.run(testConfig, environment);
        });
    }

    static class TestConfig extends Configuration {
        @Getter
        private MemqConfig memqConfig = MemqConfig.builder()
                .executors(List.of(ExecutorConfig.builder()
                        .threadPoolSize(Constants.DEFAULT_THREADPOOL)
                        .name(GLOBAL_THREADPOOL_NAME)
                        .build()))
                .build();

    }
}
