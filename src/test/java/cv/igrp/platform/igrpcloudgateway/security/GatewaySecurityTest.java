package cv.igrp.platform.igrpcloudgateway.security;

import cv.igrp.platform.igrpcloudgateway.RecordingBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Default hardening, with the test client (localhost) acting as an untrusted caller.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.kubernetes.enabled=false",
                "spring.cloud.kubernetes.discovery.enabled=false",
                "spring.cloud.gateway.server.webflux.trusted-proxies=10\\.99\\..*",
        })
class GatewaySecurityTest {

    private static final RecordingBackend backend = RecordingBackend.start();

    @LocalServerPort
    private int port;

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

    @BeforeEach
    void reset() {
        backend.reset();
    }

    @Test
    void proxiesRegularRequests() {
        webTestClient.get().uri("/backend/api/users").exchange().expectStatus().isOk();

        assertThat(backend.hits()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/backend/actuator",
            "/backend/actuator/heapdump",
            "/backend/ACTUATOR/env",
            "/backend/%61ctuator/env",
            "/backend/actuator;x=1/env",
            "/backend//actuator/env",
            "/backend/a/../actuator/env",
            "/backend/x%2Factuator/env",
            "/backend/api/actuator/env",
    })
    void neverProxiesServiceActuatorEndpoints(String path) {
        webTestClient.get().uri(URI.create("http://localhost:" + port + path))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(backend.hits()).as("request reached the service").isZero();
    }

    @Test
    void gatewayActuatorOnlyExposesHealthWithoutDetails() {
        webTestClient.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"status\":\"UP\"}")
                .jsonPath("$.components").doesNotExist();
        webTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();

        webTestClient.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
        webTestClient.get().uri("/actuator/info").exchange().expectStatus().isNotFound();
        webTestClient.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
    }

    @Test
    void cannotCreateRoutesThroughActuator() {
        webTestClient.post().uri("/actuator/gateway/routes/evil")
                .header("Content-Type", "application/json")
                .bodyValue("{\"uri\":\"http://example.org\",\"predicates\":[\"Path=/evil/**\"]}")
                .exchange()
                .expectStatus().isNotFound();
        webTestClient.post().uri("/actuator/gateway/refresh").exchange().expectStatus().isNotFound();
    }

    @Test
    void dropsForwardedHeadersFromUntrustedCallers() {
        webTestClient.get().uri("/backend/ping")
                .header("X-Forwarded-For", "6.6.6.6")
                .header("X-Forwarded-Host", "evil.example")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Port", "443")
                .header("X-Forwarded-Prefix", "/evil")
                .header("X-Real-IP", "6.6.6.6")
                .header("Forwarded", "for=6.6.6.6;host=evil.example")
                .exchange()
                .expectStatus().isOk();

        UntrustedForwardedHeadersFilter.FORWARDED_HEADERS.forEach(header ->
                assertThat(backend.lastHeader(header)).as(header).isNull());
    }

    @Test
    void untrustedCallersCannotInjectAPrefix() {
        webTestClient.get().uri("/prefixed/ping")
                .header("X-Forwarded-Prefix", "/evil")
                .exchange()
                .expectStatus().isOk();

        // Spring Cloud Gateway drops every X-Forwarded-* header for untrusted peers, the route's included
        assertThat(backend.lastHeader("X-Forwarded-Prefix")).isNull();
    }
}
