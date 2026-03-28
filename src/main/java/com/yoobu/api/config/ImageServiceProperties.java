package com.yoobu.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.image-service")
public record ImageServiceProperties(String url, String apiKey, String cdnBaseUrl) {}
