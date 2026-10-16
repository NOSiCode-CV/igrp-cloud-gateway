package cv.igrp.platform.igrpcloudgateway.observability;

import cv.igrp.platform.igrpcloudgateway.RecordingBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that requests proxied by the gateway carry the gateway's trace context downstream.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "otel.sdk.disabled=false",
                "otel.traces.exporter=none",
                "otel.metrics.exporter=none",
                "otel.logs.exporter=none",
                "eureka.client.enabled=false",
                "spring.cloud.kubernetes.enabled=false",
                "spring.cloud.kubernetes.discovery.enabled=false",
        })
class TraceContextPropagationTest {

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

    @BeforeEach
    void reset() {
        backend.reset();
    }

    @Test
    void startsTraceAndPropagatesItWhenCallerSendsNone() {
        webTestClient.get().uri("/backend/ping").exchange().expectStatus().isOk();

        assertThat(backend.lastHeader("traceparent"))
                .as("traceparent forwarded to the backend")
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    }

    @Test
    void continuesCallerTraceWithGatewaySpanAsParent() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String callerSpanId = "00f067aa0ba902b7";

        webTestClient.get().uri("/backend/ping")
                .header("traceparent", "00-" + traceId + "-" + callerSpanId + "-01")
                .exchange()
                .expectStatus().isOk();

        String[] parts = backend.lastHeader("traceparent").split("-");
        assertThat(parts[1]).as("same trace").isEqualTo(traceId);
        assertThat(parts[2]).as("parent is the gateway span, not the caller").isNotEqualTo(callerSpanId);
    }
}
