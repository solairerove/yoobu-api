package com.yoobu.api.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class TelegramBotApiClient {

    private static final String SEND_MESSAGE_URL = "https://api.telegram.org/bot{token}/sendMessage";
    private static final int MAX_ATTEMPTS = 3;
    static final long[] DEFAULT_BACKOFF_MS = {1_000L, 3_000L, 9_000L};

    private final RestClient restClient;
    private final long[] backoffMs;

    public TelegramBotApiClient() {
        this.restClient = RestClient.create();
        this.backoffMs = DEFAULT_BACKOFF_MS;
    }

    // for tests
    TelegramBotApiClient(RestClient.Builder restClientBuilder, long[] backoffMs) {
        this.restClient = restClientBuilder.build();
        this.backoffMs = backoffMs;
    }

    boolean sendMessage(String botToken, Long chatId, String text) {
        record Body(long chat_id, String text, String parse_mode) {}
        var body = new Body(chatId, text, "HTML");

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri(SEND_MESSAGE_URL, botToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.debug("Telegram message sent to chatId={}", chatId);
                return true;
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode().value() == 429) {
                    long waitMs = parseRetryAfterMs(ex.getResponseBodyAsString());
                    log.warn("Telegram rate limit (429) for chatId={}, waiting {}ms (attempt {}/{})",
                            chatId, waitMs, attempt + 1, MAX_ATTEMPTS);
                    if (attempt < MAX_ATTEMPTS - 1) {
                        sleepSafe(waitMs);
                    }
                } else {
                    log.warn("Telegram sendMessage error {} for chatId={} (attempt {}/{}): {}",
                            ex.getStatusCode().value(), chatId, attempt + 1, MAX_ATTEMPTS,
                            ex.getResponseBodyAsString());
                    if (attempt < MAX_ATTEMPTS - 1) {
                        sleepSafe(backoffMs[attempt]);
                    }
                }
            } catch (Exception ex) {
                log.warn("Telegram sendMessage failed for chatId={} (attempt {}/{}): {}",
                        chatId, attempt + 1, MAX_ATTEMPTS, ex.getMessage());
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleepSafe(backoffMs[attempt]);
                }
            }
        }
        log.warn("Telegram notification to chatId={} failed after {} attempts", chatId, MAX_ATTEMPTS);
        return false;
    }

    private long parseRetryAfterMs(String responseBody) {
        int idx = responseBody.indexOf("retry_after");
        if (idx < 0) {
            return backoffMs[0];
        }
        try {
            String after = responseBody.substring(idx + "retry_after".length());
            String digits = after.replaceFirst("[^0-9]*(\\d+).*", "$1");
            return Long.parseLong(digits) * 1_000L;
        } catch (Exception e) {
            return backoffMs[0];
        }
    }

    private void sleepSafe(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
