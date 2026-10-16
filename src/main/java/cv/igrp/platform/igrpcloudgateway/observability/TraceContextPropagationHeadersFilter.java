package cv.igrp.platform.igrpcloudgateway.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Propagates the gateway's OpenTelemetry trace context to the proxied services.
 * <p>
 * The OpenTelemetry starter opens a server span for every request reaching the gateway,
 * but Spring Cloud Gateway forwards requests through its own Reactor Netty client, which
 * is not instrumented. Without this filter the downstream services would either start a
 * new trace or attach to the caller's span, skipping the gateway hop entirely.
 * <p>
 * The configured propagators (W3C {@code traceparent}/{@code tracestate} and
 * {@code baggage} by default) overwrite any value sent by the caller, so the downstream
 * span becomes a child of the gateway span. When there is no active span (e.g. the SDK
 * is disabled) the request headers are forwarded untouched.
 */
@Component
public class TraceContextPropagationHeadersFilter implements HttpHeadersFilter, Ordered {

    private static final TextMapSetter<HttpHeaders> SETTER = HttpHeaders::set;

    private final OpenTelemetry openTelemetry;

    public TraceContextPropagationHeadersFilter(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(input);
        openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), headers, SETTER);
        return headers;
    }

    @Override
    public boolean supports(Type type) {
        return type == Type.REQUEST;
    }

    @Override
    public int getOrder() {
        // Run after the built-in filters (forwarded/X-Forwarded headers, hop-by-hop removal)
        return Ordered.LOWEST_PRECEDENCE;
    }
}
