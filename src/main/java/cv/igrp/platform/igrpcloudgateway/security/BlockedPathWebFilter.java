package cv.igrp.platform.igrpcloudgateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rejects requests that would reach a blocked path of a proxied service, e.g.
 * {@code /igrp-access-management/actuator/heapdump}.
 * <p>
 * Every segment after the first one is checked, after percent-decoding and with matrix
 * parameters removed, so variants such as {@code /svc/%61ctuator}, {@code /svc/actuator;x=1},
 * {@code /svc//actuator} or {@code /svc/a/../actuator} are caught as well. The first segment is
 * left alone so the gateway's own {@code /actuator} keeps working (it is restricted separately
 * through the {@code management.*} properties).
 * <p>
 * Blocked requests get a plain 404 so the response does not reveal that the endpoint exists.
 */
public class BlockedPathWebFilter implements WebFilter, Ordered {

    private final Set<String> blockedSegments;

    public BlockedPathWebFilter(GatewaySecurityProperties properties) {
        this.blockedSegments = properties.blockedPathSegments().stream()
                .map(segment -> segment.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!blockedSegments.isEmpty() && isBlocked(exchange.getRequest().getPath().pathWithinApplication())) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isBlocked(PathContainer path) {
        boolean first = true;
        for (PathContainer.Element element : path.elements()) {
            if (!(element instanceof PathContainer.PathSegment segment) || segment.valueToMatch().isEmpty()) {
                continue;
            }
            if (first) {
                first = false;
                continue;
            }
            // valueToMatch is decoded, so an encoded slash or backslash may hide more segments
            for (String part : segment.valueToMatch().split("[/\\\\]")) {
                if (blockedSegments.contains(part.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // Before routing and before any other filter does work on the request
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
