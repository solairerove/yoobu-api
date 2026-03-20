package com.yoobu.api.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

class TelegramUserArgumentResolverTest {

    @Test
    void supportsOnlyAnnotatedTelegramUserParameter() throws Exception {
        TelegramUserArgumentResolver resolver = new TelegramUserArgumentResolver(
                new StubTelegramInitDataValidator(),
                new MockEnvironment()
        );
        MethodParameter annotatedTelegramUser = methodParameter("annotated", TelegramUser.class);
        MethodParameter notAnnotatedTelegramUser = methodParameter("notAnnotated", TelegramUser.class);
        MethodParameter annotatedLong = methodParameter("annotated", Long.class);

        assertTrue(resolver.supportsParameter(annotatedTelegramUser));
        assertFalse(resolver.supportsParameter(notAnnotatedTelegramUser));
        assertFalse(resolver.supportsParameter(annotatedLong));
    }

    @Test
    void resolveArgumentUsesInitDataHeaderWhenProvided() throws Exception {
        StubTelegramInitDataValidator validator = new StubTelegramInitDataValidator();
        TelegramUserArgumentResolver resolver = new TelegramUserArgumentResolver(validator, new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Telegram-Init-Data", "init-data-payload");

        TelegramUser result = (TelegramUser) resolver.resolveArgument(
                methodParameter("annotated", TelegramUser.class),
                null,
                new ServletWebRequest(request),
                null
        );

        assertSame(validator.nextUser, result);
        assertEquals("init-data-payload", validator.lastRawInitData);
    }

    @Test
    void resolveArgumentUsesDevUserHeaderWhenNoInitDataAndDevProfile() throws Exception {
        TelegramUserArgumentResolver resolver = new TelegramUserArgumentResolver(
                new StubTelegramInitDataValidator(),
                new MockEnvironment().withProperty("spring.profiles.active", "dev")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Telegram-User-Id", "12345");

        TelegramUser result = (TelegramUser) resolver.resolveArgument(
                methodParameter("annotated", TelegramUser.class),
                null,
                new ServletWebRequest(request),
                null
        );

        assertEquals(12345L, result.id());
        assertEquals("Dev", result.firstName());
        assertEquals("User", result.lastName());
    }

    @Test
    void resolveArgumentRejectsInvalidDevUserId() throws Exception {
        TelegramUserArgumentResolver resolver = new TelegramUserArgumentResolver(
                new StubTelegramInitDataValidator(),
                new MockEnvironment().withProperty("spring.profiles.active", "dev")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Telegram-User-Id", "abc");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> resolver.resolveArgument(
                        methodParameter("annotated", TelegramUser.class),
                        null,
                        new ServletWebRequest(request),
                        null
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid initData", ex.getReason());
    }

    @Test
    void resolveArgumentRejectsWhenNoHeadersInNonDevProfile() throws Exception {
        TelegramUserArgumentResolver resolver = new TelegramUserArgumentResolver(
                new StubTelegramInitDataValidator(),
                new MockEnvironment().withProperty("spring.profiles.active", "prod")
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> resolver.resolveArgument(
                        methodParameter("annotated", TelegramUser.class),
                        null,
                        new ServletWebRequest(new MockHttpServletRequest()),
                        null
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid initData", ex.getReason());
    }

    private static MethodParameter methodParameter(String methodName, Class<?> parameterType) throws Exception {
        return new MethodParameter(
                TestController.class.getDeclaredMethod(methodName, parameterType),
                0
        );
    }

    private static final class StubTelegramInitDataValidator extends TelegramInitDataValidator {
        private String lastRawInitData;
        private final TelegramUser nextUser = new TelegramUser(777L, "Alice", "Tester", "alice");

        private StubTelegramInitDataValidator() {
            super(new ObjectMapper());
        }

        @Override
        public TelegramUser validate(String rawInitData) {
            this.lastRawInitData = rawInitData;
            return nextUser;
        }
    }

    @SuppressWarnings("unused")
    private static final class TestController {
        void annotated(@TelegramPrincipal TelegramUser user) {
        }

        void notAnnotated(TelegramUser user) {
        }

        void annotated(@TelegramPrincipal Long userId) {
        }
    }
}
