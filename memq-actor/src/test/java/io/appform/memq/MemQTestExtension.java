package io.appform.memq;

import io.appform.memq.actor.DispatcherType;
import io.appform.memq.helper.TestUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 *
 */
public class MemQTestExtension implements BeforeEachCallback, AfterEachCallback, TestTemplateInvocationContextProvider {
    private ActorSystem actorSystem;

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        actorSystem.close();
        Awaitility.await()
                .timeout(Duration.ofMinutes(1))
                .catchUncaughtExceptions()
                .until(() -> !actorSystem.isRunning());
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        // No-op: Initialization is handled by ParameterResolver in test template invocation context
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext extensionContext) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext extensionContext) {
        return Arrays.stream(DispatcherType.values())
                .map(this::createContext);
    }

    private TestTemplateInvocationContext createContext(DispatcherType dispatcherType) {
        return new TestTemplateInvocationContext() {
            @Override
            public String getDisplayName(int invocationIndex) {
                return "Executing with dispatcherType: " + dispatcherType.name();
            }

            @Override
            public List<Extension> getAdditionalExtensions() {
                return List.of(new ParameterResolver() {
                    @Override
                    public boolean supportsParameter(
                            ParameterContext parameterContext,
                            ExtensionContext extensionContext) throws ParameterResolutionException {
                        return parameterContext.getParameter().getType().equals(ActorSystem.class);
                    }

                    @Override
                    public Object resolveParameter(
                            ParameterContext parameterContext,
                            ExtensionContext extensionContext) throws ParameterResolutionException {
                        actorSystem = TestUtil.actorSystem(Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE),
                                dispatcherType);
                        return actorSystem;
                    }
                });
            }
        };
    }
}
