package ru.rutcampustrack.academic.integration;

import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Resets only the fresh database owned by these integration-test bases. */
public final class AcademicFixtureResetListener extends AbstractTestExecutionListener {
    // Committed fixture reset precedes Spring's test transaction (order 4000).
    @Override
    public int getOrder() {
        return 3500;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        var fixture =
                testContext
                        .getApplicationContext()
                        .getBean(AcademicIdentityIntegrationFixture.class);
        fixture.beforeTest(testContext.getApplicationContext());
        try {
            if (AbstractAcademicCacheIntegrationTest.class.isAssignableFrom(
                    testContext.getTestClass())) {
                var redis = AbstractAcademicCacheIntegrationTest.REDIS;
                var environment = testContext.getApplicationContext().getEnvironment();
                var factory =
                        testContext
                                .getApplicationContext()
                                .getBean(
                                        org.springframework.data.redis.connection
                                                .RedisConnectionFactory.class);
                if (!redis.isRunning()
                        || !(factory
                                instanceof
                                org.springframework.data.redis.connection.lettuce
                                                .LettuceConnectionFactory
                                        lettuce)
                        || !redis.getHost().equals(lettuce.getHostName())
                        || redis.getMappedPort(6379) != lettuce.getPort()
                        || !redis.getHost().equals(environment.getProperty("rct.cache.redis.host"))
                        || !Integer.valueOf(redis.getMappedPort(6379))
                                .equals(
                                        environment.getProperty(
                                                "rct.cache.redis.port", Integer.class))) {
                    throw new IllegalStateException(
                            "Cache reset requires this test's fresh owned Redis");
                }
            } else if (testContext
                            .getApplicationContext()
                            .getBeansOfType(
                                    org.springframework.data.redis.cache.RedisCacheManager.class)
                            .size()
                    > 0) {
                throw new IllegalStateException(
                        "Fixture reset cannot clear an unowned Redis cache");
            }
            testContext
                    .getApplicationContext()
                    .getBeansOfType(CacheManager.class)
                    .values()
                    .forEach(
                            manager ->
                                    manager.getCacheNames()
                                            .forEach(
                                                    name -> {
                                                        var cache = manager.getCache(name);
                                                        if (cache != null) cache.clear();
                                                    }));

        } catch (Exception failure) {
            fixture.afterTest();
            throw failure;
        }
        // Tests select an existing Auth actor explicitly. No default administrator scope is
        // installed.
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        testContext
                .getApplicationContext()
                .getBean(AcademicIdentityIntegrationFixture.class)
                .afterTest();
    }
}
