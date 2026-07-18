package com.omyadav.repodoctor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI repoDoctorOpenAPI() {

        Contact contact =
                new Contact()
                        .name("Om Yadav");

        Info apiInfo =
                new Info()
                        .title("RepoDoctor API")
                        .description(
                                "GitHub repository health, documentation, "
                                        + "structure, commit quality and code quality "
                                        + "analysis REST API."
                        )
                        .version("1.0.0")
                        .contact(contact);

        return new OpenAPI()
                .info(apiInfo);
    }
}