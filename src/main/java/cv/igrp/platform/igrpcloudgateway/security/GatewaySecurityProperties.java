package cv.igrp.platform.igrpcloudgateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Hardening settings of the gateway ({@code igrp.gateway.security.*}).
 *
 * @param blockedPathSegments path segments that are never proxied when they appear below the
 *                            root of the request path (matched case-insensitively). Defaults to
 *                            {@code actuator}, which keeps the services' actuator endpoints private.
 */
@ConfigurationProperties(prefix = "igrp.gateway.security")
public record GatewaySecurityProperties(List<String> blockedPathSegments) {

    public GatewaySecurityProperties {
        blockedPathSegments = blockedPathSegments == null
                ? List.of()
                : blockedPathSegments.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
