package com.valor.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class Db37SpringTestConfiguration {
    @Bean
    Db37ConnectionCapture db37ConnectionCapture() {
        return new Db37ConnectionCapture();
    }

    @Bean(destroyMethod = "close")
    @Primary
    HikariDataSource db37DataSource(Db37ConnectionCapture capture) throws SQLException {
        Db37MysqlTestHarness.Configuration configuration = Db37MysqlTestHarness.fromEnvironment();
        if (!"valor_backend_db37_gopi01".equals(configuration.schema())) {
            throw new IllegalStateException("DB37 test requires valor_backend_db37_gopi01");
        }
        HikariDataSource dataSource = Db37MysqlTestHarness
                .createVerifiedFailureInjectedDataSource(configuration, capture::observe, capture::observeEviction);
        return dataSource;
    }
}
