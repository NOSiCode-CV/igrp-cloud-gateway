package cv.igrp.platform.igrpcloudgateway.security;

import cv.igrp.platform.igrpcloudgateway.RecordingBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test client (localhost) acts as a trusted proxy, e.g. the ingress controller.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.kubernetes.enabled=false",
                "spring.cloud.kubernetes.discovery.enabled=false",
                "spring.cloud.gateway.server.webflux.trusted-proxies=127\\.0\\.0\\.1|0:0:0:0:0:0:0:1",
        })
class TrustedProxyForwardedHeadersTest {

    private static final RecordingBackend backend = RecordingBackend.start();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        backend.registerRoute(registry);
    }

    @AfterAll
    static void stopBackend() {
        backend.stop();
    }

    @Test
    void keepsForwardedHeadersFromTrustedProxies() {
        webTestClient.get().uri("/backend/ping")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Forwarded-Host", "api.example.cv")
                .header("X-Forwarded-Prefix", "/igrp")
                .exchange()
                .expectStatus().isOk();

        // Forwarded exactly as the proxy sent them: the gateway appends nothing
        assertThat(backend.lastHeader("X-Forwarded-For")).as("client IP reported by the proxy").isEqualTo("203.0.113.7");
        assertThat(backend.lastHeader("X-Forwarded-Host")).isEqualTo("api.example.cv");
        assertThat(backend.lastHeader("X-Forwarded-Prefix")).isEqualTo("/igrp");
        assertThat(backend.lastHeader("Forwarded")).isNull();
    }

    @Test
    void routePrefixReplacesTheProxyPrefix() {
        webTestClient.get().uri("/prefixed/ping")
                .header("X-Forwarded-Prefix", "/igrp")
                .exchange()
                .expectStatus().isOk();

        assertThat(backend.lastHeader("X-Forwarded-Prefix")).isEqualTo("/prefixed");
    }
}
