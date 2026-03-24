package com.yoobu.api;

import com.yoobu.api.config.CorsProperties;
import com.yoobu.api.config.MediaProperties;
import com.yoobu.api.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({SecurityProperties.class, CorsProperties.class, MediaProperties.class})
public class YoobuApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoobuApplication.class, args);
    }
}
