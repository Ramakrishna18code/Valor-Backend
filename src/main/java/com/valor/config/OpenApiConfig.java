package com.valor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI valorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Valor Lift Services & Maintenance API")
                        .description("Enterprise backend for lift services and maintenance management")
                        .version("1.0.0"));
    }
}
