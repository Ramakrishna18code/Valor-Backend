package com.valor.configuration;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("mysql-integration")
public class MysqlIntegrationSafetyGuard {
    private final Environment environment;

    public MysqlIntegrationSafetyGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateDedicatedDatabase() {
        String url = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");

        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("MySQL integration URL must use jdbc:mysql://");
        }

        String hostAndDatabase = url.substring("jdbc:mysql://".length());
        int slash = hostAndDatabase.indexOf('/');
        if (slash < 0) {
            throw new IllegalStateException("MySQL integration URL must include a database name");
        }

        String hostPort = hostAndDatabase.substring(0, slash).toLowerCase(Locale.ROOT);
        String database = hostAndDatabase.substring(slash + 1).split("[?&]", 2)[0].toLowerCase(Locale.ROOT);
        boolean localHost = hostPort.startsWith("127.0.0.1") || hostPort.startsWith("localhost");

        if (!localHost) {
            throw new IllegalStateException("MySQL integration tests must use a local database");
        }
        if (!"valor_integration_test".equals(database)) {
            throw new IllegalStateException("MySQL integration tests must use valor_integration_test");
        }
        if (!"valor_test".equals(username)) {
            throw new IllegalStateException("MySQL integration tests must use the valor_test account");
        }
        if (password.isBlank()) {
            throw new IllegalStateException("MYSQL_INTEGRATION_DB_PASSWORD is required");
        }
    }
}
