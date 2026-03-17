package com.yoobu.api.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://localhost:*",
            "https://127.0.0.1:*",
            "https://*.up.railway.app"
    );

    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

    private List<String> allowedHeaders = List.of(
            "Authorization",
            "Content-Type",
            "X-Telegram-Init-Data",
            "X-Telegram-User-Id"
    );

    private List<String> exposedHeaders = List.of("WWW-Authenticate");

    private boolean allowCredentials = true;

    private Long maxAge = 3600L;
}
