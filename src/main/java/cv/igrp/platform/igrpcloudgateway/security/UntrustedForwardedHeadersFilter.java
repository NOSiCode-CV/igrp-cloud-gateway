package cv.igrp.platform.igrpcloudgateway.security;

import org.springframework.cloud.gateway.filter.headers.TrustedProxies;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/**
 * Drops the proxy headers ({@code Forwarded}, {@code X-Forwarded-*}, {@code X-Real-IP}) sent by
 * callers that are not trusted proxies, as soon as the request reaches the gateway.
 * <p>
 * Without this filter anyone could make a service believe the request came from another IP, host
 * or prefix — which the services use for audit logs and to build absolute URLs (OIDC issuer,
 * redirects, links in e-mails). Requests from trusted proxies keep their headers untouched.
 * <p>
 * It runs before routing, so headers set by the routes themselves (e.g. the production discovery
 * routes' {@code SetRequestHeader X-Forwarded-Prefix}) are forwarded for every caller. For the same
 * reason Spring Cloud Gateway's own {@code Forwarded} / {@code X-Forwarded-*} filters are disabled in
 * {@code application.yml}: they would append the gateway hop to the route's prefix or strip it.
 * <p>
 * Trusted proxies are the ones matched by {@code spring.cloud.gateway.server.webflux.trusted-proxies}
 * (a Java regex on the IP, e.g. the ingress controller's pod network). The check uses the TCP peer of
 * the connection: once trusted proxies are configured, Reactor Netty resolves {@code getRemoteAddress()}
 * from the forwarded headers of trusted peers, so it would return the original client instead.
 */
public class UntrustedForwardedHeadersFilter implements WebFilter, Ordered {

    static final List<String> FORWARDED_HEADERS = List.of(
            "Forwarded",
            "X-Forwarded-For",
            "X-Forwarded-Host",
            "X-Forwarded-Port",
            "X-Forwarded-Proto",
            "X-Forwarded-Prefix",
            "X-Real-IP");

    private final TrustedProxies trustedProxies;

    public UntrustedForwardedHeadersFilter(TrustedProxies trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isTrusted(connectionPeer(request))
                || FORWARDED_HEADERS.stream().noneMatch(request.getHeaders()::containsKey)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> FORWARDED_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    private static SocketAddress connectionPeer(ServerHttpRequest request) {
        Object nativeRequest = ServerHttpRequestDecorator.getNativeRequest(request);
        if (nativeRequest instanceof HttpServerRequest nettyRequest) {
            return nettyRequest.connectionRemoteAddress();
        }
        return request.getRemoteAddress();
    }

    private boolean isTrusted(SocketAddress peer) {
        return peer instanceof InetSocketAddress address
                && address.getAddress() != null
                && trustedProxies.isTrusted(address.getAddress().getHostAddress());
    }

    @Override
    public int getOrder() {
        // Right after BlockedPathWebFilter, before the OpenTelemetry filter and routing
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
