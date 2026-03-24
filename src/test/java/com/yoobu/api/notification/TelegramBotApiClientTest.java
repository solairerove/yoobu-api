package com.yoobu.api.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramBotApiClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TelegramBotApiClient client = new TelegramBotApiClient(builder, new long[]{0L, 0L, 0L});

    @Test
    void sendMessage_success_returnsTrue() {
        server.expect(ExpectedCount.once(), requestTo(urlFor("my-token")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertTrue(client.sendMessage("my-token", 123L, "Hello"));
        server.verify();
    }

    @Test
    void sendMessage_429thenSuccess_retriesAndReturnsTrue() {
        server.expect(ExpectedCount.once(), requestTo(urlFor("my-token")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"ok\":false,\"parameters\":{\"retry_after\":0}}")
                        .contentType(MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(urlFor("my-token")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertTrue(client.sendMessage("my-token", 123L, "Hello"));
        server.verify();
    }

    @Test
    void sendMessage_3xServerError_returnsFalse() {
        server.expect(ExpectedCount.times(3), requestTo(urlFor("my-token")))
                .andRespond(withServerError());

        assertFalse(client.sendMessage("my-token", 123L, "Hello"));
        server.verify();
    }

    private static String urlFor(String token) {
        return "https://api.telegram.org/bot" + token + "/sendMessage";
    }
}
