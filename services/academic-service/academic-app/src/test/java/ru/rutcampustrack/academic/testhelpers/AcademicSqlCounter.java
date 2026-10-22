package ru.rutcampustrack.academic.testhelpers;

import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

/** Counts actual JDBC executions on the owning application's DataSource. */
public final class AcademicSqlCounter implements BeanPostProcessor {
    private final AtomicLong statements = new AtomicLong();

    public void reset() {
        statements.set(0);
    }

    public long count() {
        return statements.get();
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof DataSource source)) return bean;
        return Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    Object result = invoke(source, method, args);
                    if (result instanceof Connection connection) return connection(connection);
                    return result;
                });
    }

    private Connection connection(Connection target) {
        return (Connection)
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            Object result = invoke(target, method, args);
                            if (result instanceof Statement statement) {
                                Class<?> type =
                                        statement instanceof PreparedStatement
                                                ? PreparedStatement.class
                                                : Statement.class;
                                return Proxy.newProxyInstance(
                                        type.getClassLoader(),
                                        new Class<?>[] {type},
                                        (ignored, call, parameters) -> {
                                            if (call.getName().startsWith("execute"))
                                                statements.incrementAndGet();
                                            return invoke(statement, call, parameters);
                                        });
                            }
                            return result;
                        });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
