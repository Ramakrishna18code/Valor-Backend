package com.valor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ValorBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValorBackendApplication.class, args);
    }
}
