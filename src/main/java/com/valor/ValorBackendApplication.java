package com.valor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.valor.auth", "com.valor.assets", "com.valor.workflow"})
@EntityScan(basePackages = {"com.valor.auth", "com.valor.assets", "com.valor.workflow"})
@EnableJpaRepositories(basePackages = {"com.valor.auth", "com.valor.assets", "com.valor.workflow"})
@EnableScheduling
public class ValorBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValorBackendApplication.class, args);
    }
}
