package com.example.autoposttotelegram.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AutoPostToTelegram API")
                        .version("1.0.0")
                        .description("API для управления постами и авторизацией в Telegram"))
                .servers(List.of(
                        new Server()
                                .url("https://ab1t.top/api")
                                .description("Production server")
                ));
    }
}