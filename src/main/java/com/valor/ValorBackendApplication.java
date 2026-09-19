package com.valor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.valor.auth", "com.valor.assets", "com.valor.workflow", "com.valor.notifications", "com.valor.commerce", "com.valor.tracking", "com.valor.communication"})
@EntityScan(basePackages = {"com.valor.auth", "com.valor.assets", "com.valor.workflow", "com.valor.notifications", "com.valor.commerce", "com.valor.tracking", "com.valor.communication"})
@EnableJpaRepositories(basePackages = {"com.valor.auth", "com.valor.assets", "com.valor.workflow", "com.valor.notifications", "com.valor.commerce", "com.valor.tracking", "com.valor.communication"})
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = {"com.valor.communication", "com.valor.auth"})
public class ValorBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValorBackendApplication.class, args);
    }
}
