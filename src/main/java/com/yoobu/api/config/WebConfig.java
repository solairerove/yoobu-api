package com.yoobu.api.config;

import com.yoobu.api.admin.AdminAuthInterceptor;
import com.yoobu.api.admin.SuperAdminAuthInterceptor;
import com.yoobu.api.tenant.TenantResolver;
import com.yoobu.api.telegram.TelegramUserArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final SuperAdminAuthInterceptor superAdminAuthInterceptor;
    private final TenantResolver tenantResolver;
    private final TelegramUserArgumentResolver telegramUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantResolver)
                .addPathPatterns("/t/*/**", "/admin/*/**");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/*/**");

        registry.addInterceptor(superAdminAuthInterceptor)
                .addPathPatterns("/superadmin/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(telegramUserArgumentResolver);
    }
}
