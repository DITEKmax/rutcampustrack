package ru.rutcampustrack.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisReportDownloadTicketStoreContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    PersistenceExceptionTranslationAutoConfiguration.class))
            .withUserConfiguration(TicketStoreConfiguration.class);

    @Test
    void repositoryStartsWithBootExceptionTranslationProxy() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PersistenceExceptionTranslationPostProcessor.class);
            assertThat(context).hasSingleBean(ReportDownloadTicketStore.class);
            assertThat(AopUtils.isCglibProxy(context.getBean(ReportDownloadTicketStore.class))).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TicketStoreConfiguration {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return new StringRedisTemplate(org.mockito.Mockito.mock(RedisConnectionFactory.class));
        }

        @Bean
        RedisReportDownloadTicketStore redisReportDownloadTicketStore(
                StringRedisTemplate stringRedisTemplate
        ) {
            return new RedisReportDownloadTicketStore(stringRedisTemplate);
        }
    }
}