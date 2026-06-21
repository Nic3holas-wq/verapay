package com.nicko.verapay.payments.service;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nicko.verapay.config.MpesaConfig;
import com.nicko.verapay.dto.mpesa.B2CResponseDto;
import com.nicko.verapay.dto.mpesa.StkPushResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {
                MpesaAuthService.class,
                MpesaB2CService.class,
                MpesaC2BService.class,
                MpesaConfig.class,
                TestRestClientConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Import(TestRestClientConfig.class)
@TestPropertySource(properties = {
        "mpesa.consumer.key=test-key",
        "mpesa.consumer.secret=test-secret",
        "mpesa.business.shortcode=174379",
        "mpesa.passkey=test-passkey",
        "mpesa.initiator.name=testapi",
        "mpesa.security.credential=test-credential",
        "mpesa.b2c.shortcode=600996",
        "mpesa.allowed.ips=127.0.0.1",
        "mpesa.webhook.token=test-token"
        // mpesa.base.url and mpesa.callback.base.url are now supplied dynamically!
})

class MpesaIntegrationTest {

    @Autowired
    private MpesaAuthService authService;

    @Autowired
    private MpesaB2CService b2cService;

    @Autowired
    private MpesaC2BService c2bService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "cachedToken", null);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "tokenExpiryTime", null);
    }

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry){
        registry.add("mpesa.base.url", wireMock::baseUrl);
        registry.add("mpesa.callback.base.url", wireMock::baseUrl);
        configureFor("localhost", wireMock.getPort());
    }

    // ── MpesaAuthService ──────────────────────────────────────

    @Test
    void testGetAccessTokenSuccess() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .withQueryParam("grant_type", equalTo("client_credentials"))
                .withHeader("Authorization", matching("Basic .*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        String token = authService.getAccessToken();
        assertEquals("mock-token", token);
    }

    @Test
    void testGetAccessToken_CachesTokenAndOnlyCallsSafaricomOnce() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .withQueryParam("grant_type", equalTo("client_credentials"))
                .withHeader("Authorization", matching("Basic .*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        String firstCall = authService.getAccessToken();
        String secondCall = authService.getAccessToken();

        assertEquals("mock-token", firstCall);
        assertEquals("mock-token", secondCall);

        // Confirms caching actually works — only ONE real HTTP call made
        verify(1, getRequestedFor(urlPathEqualTo("/oauth/v1/generate")));
    }

    @Test
    void testGetAccessToken_SafaricomReturns500_PropagatesException() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(Exception.class, () -> authService.getAccessToken());
    }

    // ── MpesaB2CService ───────────────────────────────────────

    @Test
    void testInitiateB2CSuccess() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        stubFor(post(urlEqualTo("/mpesa/b2c/v3/paymentrequest"))
                .withHeader("Authorization", equalTo("Bearer mock-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ResponseCode\":\"0\",\"ResponseDescription\":\"Accept The Service Request successfully\",\"ConversationID\":\"c_id_123\",\"OriginatorConversationID\":\"o_id_123\"}")));

        B2CResponseDto response = b2cService.initiateB2C(
                "0712345678",
                new BigDecimal("1000"),
                "ref123"
        );

        assertNotNull(response);
        assertEquals("0", response.responseCode());
        assertEquals("c_id_123", response.conversationID());
    }

    @Test
    void testInitiateB2C_NonZeroResponseCode_ReturnsFailureResponse() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        stubFor(post(urlEqualTo("/mpesa/b2c/v3/paymentrequest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ResponseCode\":\"1\",\"ResponseDescription\":\"Insufficient funds in organization account\",\"ConversationID\":\"\",\"OriginatorConversationID\":\"\"}")));

        B2CResponseDto response = b2cService.initiateB2C(
                "0712345678", new BigDecimal("1000"), "ref123");

        assertNotNull(response);
        assertEquals("1", response.responseCode());
        assertEquals("Insufficient funds in organization account",
                response.responseDescription());
    }

    @Test
    void testInitiateB2C_SafaricomReturns500_PropagatesException() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        stubFor(post(urlEqualTo("/mpesa/b2c/v3/paymentrequest"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(Exception.class, () ->
                b2cService.initiateB2C("0712345678", new BigDecimal("1000"), "ref123"));
    }

    // ── MpesaC2BService ───────────────────────────────────────

    @Test
    void testInitiateStkPushSuccess() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        stubFor(post(urlEqualTo("/mpesa/stkpush/v1/processrequest"))
                .withHeader("Authorization", equalTo("Bearer mock-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"MerchantRequestID\":\"mr_123\",\"CheckoutRequestID\":\"cr_123\",\"ResponseCode\":\"0\",\"ResponseDescription\":\"Success\",\"CustomerMessage\":\"Success\"}")));

        StkPushResponseDto response = c2bService.initiateStkPush(
                "0712345678",
                new BigDecimal("500"),
                "idempotency-123"
        );

        assertNotNull(response);
        assertEquals("0", response.responseCode());
        assertEquals("cr_123", response.checkoutRequestID());
    }

    @Test
    void testInitiateStkPush_NonZeroResponseCode_ReturnsFailureResponse() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        stubFor(post(urlEqualTo("/mpesa/stkpush/v1/processrequest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"MerchantRequestID\":\"mr_123\",\"CheckoutRequestID\":\"cr_123\",\"ResponseCode\":\"1\",\"ResponseDescription\":\"Unable to lock subscriber\",\"CustomerMessage\":\"Failed\"}")));

        StkPushResponseDto response = c2bService.initiateStkPush(
                "0712345678", new BigDecimal("500"), "idempotency-123");

        assertNotNull(response);
        assertEquals("1", response.responseCode());
    }

    @Test
    void testInitiateStkPush_SafaricomReturns500_PropagatesException() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        stubFor(post(urlEqualTo("/mpesa/stkpush/v1/processrequest"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(Exception.class, () ->
                c2bService.initiateStkPush("0712345678", new BigDecimal("500"), "idempotency-123"));
    }

    @Test
    void testInitiateStkPush_MalformedJsonResponse_PropagatesException() {
        stubFor(get(urlPathEqualTo("/oauth/v1/generate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-token\",\"expires_in\":\"3600\"}")));

        stubFor(post(urlEqualTo("/mpesa/stkpush/v1/processrequest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not-valid-json")));

        assertThrows(Exception.class, () ->
                c2bService.initiateStkPush("0712345678", new BigDecimal("500"), "idempotency-123"));
    }
}