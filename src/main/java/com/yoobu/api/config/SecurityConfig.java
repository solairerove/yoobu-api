package com.yoobu.api.config;

import com.yoobu.api.security.SuperAdminBasicAuthenticationFilter;
import com.yoobu.api.security.TenantBasicAuthenticationFilter;
import com.yoobu.api.security.TenantContextFilter;
import com.yoobu.api.tenant.TenantRepository;
import com.yoobu.api.tenant.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Bean
    TenantContextFilter tenantContextFilter(TenantRepository tenantRepository) {
        return new TenantContextFilter(tenantRepository);
    }

    @Bean
    TenantBasicAuthenticationFilter tenantBasicAuthenticationFilter(
            TenantSettingsService tenantSettingsService,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties
    ) {
        return new TenantBasicAuthenticationFilter(tenantSettingsService, passwordEncoder, securityProperties);
    }

    @Bean
    SuperAdminBasicAuthenticationFilter superAdminBasicAuthenticationFilter(SecurityProperties securityProperties) {
        return new SuperAdminBasicAuthenticationFilter(securityProperties);
    }

    @Bean
    FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(TenantContextFilter tenantContextFilter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(tenantContextFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<TenantBasicAuthenticationFilter> tenantBasicAuthenticationFilterRegistration(
            TenantBasicAuthenticationFilter tenantBasicAuthenticationFilter
    ) {
        FilterRegistrationBean<TenantBasicAuthenticationFilter> registration =
                new FilterRegistrationBean<>(tenantBasicAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<SuperAdminBasicAuthenticationFilter> superAdminBasicAuthenticationFilterRegistration(
            SuperAdminBasicAuthenticationFilter superAdminBasicAuthenticationFilter
    ) {
        FilterRegistrationBean<SuperAdminBasicAuthenticationFilter> registration =
                new FilterRegistrationBean<>(superAdminBasicAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain superAdminSecurityFilterChain(
            HttpSecurity http,
            SuperAdminBasicAuthenticationFilter superAdminBasicAuthenticationFilter
    ) throws Exception {
        http
                .securityMatcher("/superadmin/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(superAdminBasicAuthenticationFilter, BasicAuthenticationFilter.class)
                .anonymous(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            TenantContextFilter tenantContextFilter,
            TenantBasicAuthenticationFilter tenantBasicAuthenticationFilter
    ) throws Exception {
        http
                .securityMatcher("/admin/*/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(tenantContextFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(tenantBasicAuthenticationFilter, TenantContextFilter.class)
                .anonymous(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain tenantPublicSecurityFilterChain(
            HttpSecurity http,
            TenantContextFilter tenantContextFilter
    ) throws Exception {
        http
                .securityMatcher("/t/*/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterBefore(tenantContextFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults());
        return http.build();
    }
}
