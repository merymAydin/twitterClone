package com.example.twitter_challenge;

import com.example.twitter_challenge.Service.StripeWebhookService;
import com.example.twitter_challenge.controller.StripeWebhookController;
import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String PAYLOAD = """
            {"id":"evt_1","object":"event","api_version":"2026-01-01","created":1700000000,
             "type":"customer.subscription.updated",
             "data":{"object":{"id":"sub_1","object":"subscription","customer":"cus_1",
                     "status":"active"}}}
            """;

    private StripeWebhookService webhookService;
    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        webhookService = mock(StripeWebhookService.class);
        controller = new StripeWebhookController(webhookService);
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
    }

    @Test
    void validSignatureIsProcessed() throws Exception {
        String signature = sign(PAYLOAD, Instant.now().getEpochSecond());

        ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, signature);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(webhookService).handle(eventCaptor.capture());
        assertEquals("evt_1", eventCaptor.getValue().getId());
        assertEquals("customer.subscription.updated", eventCaptor.getValue().getType());
    }

    @Test
    void invalidSignatureIsRejectedBeforeProcessing() throws Exception {
        String signature = "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef";

        ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, signature);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(webhookService, never()).handle(any());
    }

    @Test
    void unsignedPayloadIsRejected() {
        ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(webhookService, never()).handle(any());
    }

    @Test
    void missingWebhookSecretDoesNotTrustAnyPayload() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, "t=1,v1=abc");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        verify(webhookService, never()).handle(any());
    }

    @Test
    void processingFailureReturnsNon2xxSoStripeRetries() throws Exception {
        String signature = sign(PAYLOAD, Instant.now().getEpochSecond());
        doThrow(new IllegalStateException("boom")).when(webhookService).handle(any());

        ResponseEntity<String> response = controller.handleWebhook(PAYLOAD, signature);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    private String sign(String payload, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signedPayload = timestamp + "." + payload;
        byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
