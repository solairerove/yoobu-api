package com.yoobu.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    private String r2Endpoint;
    private String r2AccessKey;
    private String r2SecretKey;
    private String r2Bucket;
    private String cdnBaseUrl;
}
