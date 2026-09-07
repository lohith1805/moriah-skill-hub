package com.moriah.skillhub.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc metadata. The bearer-JWT security scheme is added at feature 03 once
 * authentication exists. Swagger UI itself is enabled only on the {@code dev} profile —
 * see {@code application-prod.yml}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI skillHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Moriah Skill Hub API")
                        .description("Backend API for the Moriah Skill Hub EdTech platform.")
                        .version("v1")
                        .contact(new Contact().name("Moriah Skill Hub Engineering")));
    }
}
