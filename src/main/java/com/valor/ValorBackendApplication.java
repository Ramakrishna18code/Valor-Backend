package com.valor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.valor.auth")
@EntityScan(basePackages = "com.valor.auth")
@EnableJpaRepositories(basePackages = "com.valor.auth")
@EnableScheduling
public class ValorBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValorBackendApplication.class, args);
    }
}
