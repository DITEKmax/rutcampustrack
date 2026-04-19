package ru.rutcampustrack.shared.web.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig
@ContextConfiguration(classes = AdminActionAspectTest.TestConfig.class)
class AdminActionAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    @ComponentScan(basePackageClasses = AdminActionAspect.class)
    static class TestConfig {
    }

    @Component
    static class Target {

        @AdminAction("test.action")
        public String execute(String payload) {
            return "result:" + payload;
        }

        @AdminAction("test.error")
        public void fail() {
            throw new IllegalStateException("boom");
        }

        public String noAnnotation(String x) {
            return "plain:" + x;
        }
    }

    @Autowired
    private Target target;

    @Test
    @DisplayName("@AdminAction pointcut проксирует метод и возвращает результат proceed()")
    void aspectInterceptsAndReturnsResult() {
        String result = target.execute("x");
        assertThat(result).isEqualTo("result:x");
        // Spring обернул bean в AOP proxy (CGLIB) — подтверждение что pointcut матчит.
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(target)).isTrue();
    }

    @Test
    @DisplayName("@AdminAction pointcut пропускает исключения (не глотает)")
    void aspectPropagatesExceptions() {
        assertThatThrownBy(target::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    @DisplayName("Методы без @AdminAction — возвращают результат (aspect no-op на них)")
    void unannotatedMethodIgnored() {
        assertThat(target.noAnnotation("y")).isEqualTo("plain:y");
    }
}
