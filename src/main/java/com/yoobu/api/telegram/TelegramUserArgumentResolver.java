package com.yoobu.api.telegram;

import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TelegramUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_TELEGRAM_INIT_DATA = "X-Telegram-Init-Data";
    private static final String HEADER_TELEGRAM_USER_ID = "X-Telegram-User-Id";

    private final TelegramInitDataValidator telegramInitDataValidator;
    private final Environment environment;

    public TelegramUserArgumentResolver(
            TelegramInitDataValidator telegramInitDataValidator,
            Environment environment
    ) {
        this.telegramInitDataValidator = telegramInitDataValidator;
        this.environment = environment;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(TelegramPrincipal.class)
                && TelegramUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String initData = webRequest.getHeader(HEADER_TELEGRAM_INIT_DATA);
        if (StringUtils.hasText(initData)) {
            return telegramInitDataValidator.validate(initData);
        }

        if (environment.matchesProfiles("dev")) {
            String userIdHeader = webRequest.getHeader(HEADER_TELEGRAM_USER_ID);
            if (StringUtils.hasText(userIdHeader)) {
                try {
                    return new TelegramUser(Long.parseLong(userIdHeader), "Dev", "User", null);
                } catch (NumberFormatException ex) {
                    throw invalidInitData();
                }
            }
        }

        throw invalidInitData();
    }

    private ResponseStatusException invalidInitData() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid initData");
    }
}
