package cv.igrp.platform.igrpcloudgateway.security;

import cv.igrp.platform.igrpcloudgateway.RecordingBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.headers.TrustedProxies;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forwarded-header handling with the shipped defaults, i.e. without trusted-proxies being configured
 * by the deployment. The test client connects from localhost, a private address.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.kubernetes.enabled=false",
                "spring.cloud.kubernetes.discovery.enabled=false",
        })
class DefaultForwardedHeadersTest {

    private static final RecordingBackend backend = RecordingBackend.start();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private Environment environment;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        backend.registerRoute(registry);
    }

    @AfterAll
    static void stopBackend() {
        backend.stop();
    }

    @Test
    void forwardsIngressHeadersUnchanged() {
        webTestClient.get().uri("/backend/ping")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Forwarded-Host", "api.example.cv")
                .header("X-Forwarded-Proto", "https")
                .exchange()
                .expectStatus().isOk();

        assertThat(backend.lastHeader("X-Forwarded-For")).isEqualTo("203.0.113.7");
        assertThat(backend.lastHeader("X-Forwarded-Host")).isEqualTo("api.example.cv");
        assertThat(backend.lastHeader("X-Forwarded-Proto")).isEqualTo("https");
        assertThat(backend.lastHeader("Forwarded")).isNull();
    }

    @Test
    void keepsThePrefixSetByTheRoute() {
        webTestClient.get().uri("/prefixed/ping")
                .header("X-Forwarded-Host", "api.example.cv")
                .exchange()
                .expectStatus().isOk();

        assertThat(backend.lastHeader("X-Forwarded-Prefix")).isEqualTo("/prefixed");
        assertThat(backend.lastHeader("X-Forwarded-Host")).isEqualTo("api.example.cv");
    }

    @Test
    void defaultTrustedProxiesCoverOnlyPrivateAddresses() {
        TrustedProxies trusted = TrustedProxies.from(environment.getProperty(TrustedProxies.PROPERTY));

        assertThat(trusted.isTrusted("10.42.3.17")).isTrue();
        assertThat(trusted.isTrusted("172.16.0.1")).isTrue();
        assertThat(trusted.isTrusted("172.31.255.254")).isTrue();
        assertThat(trusted.isTrusted("192.168.1.10")).isTrue();
        assertThat(trusted.isTrusted("127.0.0.1")).isTrue();
        assertThat(trusted.isTrusted("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(trusted.isTrusted("fd12:3456:789a:1:0:0:0:1")).isTrue();

        assertThat(trusted.isTrusted("172.32.0.1")).isFalse();
        assertThat(trusted.isTrusted("11.0.0.1")).isFalse();
        assertThat(trusted.isTrusted("203.0.113.7")).isFalse();
        assertThat(trusted.isTrusted("8.8.8.8")).isFalse();
        assertThat(trusted.isTrusted("2001:db8:0:0:0:0:0:1")).isFalse();
    }
}
