package com.yoobu.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class SecurityProperties {

    @Valid
    private final SuperAdmin superadmin = new SuperAdmin();

    @Getter
    @Setter
    public static class SuperAdmin {

        @NotBlank
        private String username;

        @NotBlank
        private String password;
    }
}
