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
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
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
            log.debug("Resolving Telegram user from initData for path={}", requestPath(webRequest));
            return telegramInitDataValidator.validate(initData);
        }

        if (environment.matchesProfiles("dev")) {
            String userIdHeader = webRequest.getHeader(HEADER_TELEGRAM_USER_ID);
            if (StringUtils.hasText(userIdHeader)) {
                try {
                    log.info("Resolving Telegram user from dev header for path={} userId={}", requestPath(webRequest), userIdHeader);
                    return new TelegramUser(Long.parseLong(userIdHeader), "Dev", "User", null);
                } catch (NumberFormatException ex) {
                    log.warn("Invalid dev Telegram user id header for path={} value={}", requestPath(webRequest), userIdHeader);
                    throw invalidInitData();
                }
            }
        }

        log.warn("Telegram auth failed for path={} initDataPresent={} devProfile={} devUserIdPresent={}",
                requestPath(webRequest),
                StringUtils.hasText(initData),
                environment.matchesProfiles("dev"),
                StringUtils.hasText(webRequest.getHeader(HEADER_TELEGRAM_USER_ID)));
        throw invalidInitData();
    }

    private String requestPath(NativeWebRequest webRequest) {
        return webRequest.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class) != null
                ? webRequest.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class).getRequestURI()
                : "unknown";
    }

    private ResponseStatusException invalidInitData() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid initData");
    }
}
