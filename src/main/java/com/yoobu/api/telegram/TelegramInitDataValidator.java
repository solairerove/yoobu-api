package com.yoobu.api.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantContext;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramInitDataValidator {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String WEB_APP_DATA = "WebAppData";

    private final ObjectMapper objectMapper;

    public TelegramUser validate(String rawInitData) {
        try {
            if (!StringUtils.hasText(rawInitData)) {
                log.warn("Telegram initData validation failed: header is blank");
                throw invalidInitData();
            }

            Map<String, String> params = parseQueryString(rawInitData);
            String hashHex = params.remove("hash");
            if (!StringUtils.hasText(hashHex)) {
                log.warn("Telegram initData validation failed: hash is missing");
                throw invalidInitData();
            }

            Tenant tenant = TenantContext.getCurrentTenant();
            if (tenant == null || !StringUtils.hasText(tenant.getBotToken())) {
                log.warn("Telegram initData validation failed: tenant or bot token missing");
                throw invalidInitData();
            }

            String dataCheckString = params.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));

            byte[] secretKey = hmac(
                    WEB_APP_DATA.getBytes(StandardCharsets.UTF_8),
                    tenant.getBotToken().getBytes(StandardCharsets.UTF_8)
            );
            byte[] expectedHash = hmac(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
            byte[] actualHash = hexToBytes(hashHex);

            if (!MessageDigest.isEqual(expectedHash, actualHash)) {
                log.warn("Telegram initData validation failed: signature mismatch for slug={}", tenant.getSlug());
                throw invalidInitData();
            }

            String userJson = params.get("user");
            if (!StringUtils.hasText(userJson)) {
                log.warn("Telegram initData validation failed: user payload is missing for slug={}", tenant.getSlug());
                throw invalidInitData();
            }

            JsonNode userNode = objectMapper.readTree(userJson);
            if (!userNode.hasNonNull("id")) {
                log.warn("Telegram initData validation failed: user id is missing for slug={}", tenant.getSlug());
                throw invalidInitData();
            }

            log.info("Telegram initData validation succeeded for slug={} userId={}", tenant.getSlug(), userNode.get("id").asLong());
            return new TelegramUser(
                    userNode.get("id").asLong(),
                    textOrNull(userNode, "first_name"),
                    textOrNull(userNode, "last_name"),
                    textOrNull(userNode, "username")
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Telegram initData validation failed: {}", ex.getClass().getSimpleName());
            throw invalidInitData();
        }
    }

    private Map<String, String> parseQueryString(String rawInitData) {
        Map<String, String> params = new TreeMap<>();
        String[] pairs = rawInitData.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            int separatorIndex = pair.indexOf('=');
            String key = separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair;
            String value = separatorIndex >= 0 ? pair.substring(separatorIndex + 1) : "";
            params.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }
        return params;
    }

    private byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA_256);
        mac.init(new SecretKeySpec(key, HMAC_SHA_256));
        return mac.doFinal(data);
    }

    private byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw invalidInitData();
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw invalidInitData();
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private ResponseStatusException invalidInitData() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid initData");
    }
}
