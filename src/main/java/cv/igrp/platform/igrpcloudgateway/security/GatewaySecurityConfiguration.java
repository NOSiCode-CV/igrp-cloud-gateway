package cv.igrp.platform.igrpcloudgateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.headers.TrustedProxies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.server.WebFilter;

/**
 * Wires the gateway hardening filters. See {@link BlockedPathWebFilter} and
 * {@link UntrustedForwardedHeadersFilter}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityConfiguration.class);

    @Bean
    BlockedPathWebFilter blockedPathWebFilter(GatewaySecurityProperties properties) {
        log.info("Blocking proxied paths containing segments {}", properties.blockedPathSegments());
        return new BlockedPathWebFilter(properties);
    }

    @Bean
    WebFilter untrustedForwardedHeadersFilter(Environment environment) {
        String trustedProxies = environment.getProperty(TrustedProxies.PROPERTY);
        if (!StringUtils.hasText(trustedProxies)) {
            log.warn("{} is empty: Spring Cloud Gateway strips every X-Forwarded-* header, including the "
                    + "X-Forwarded-Prefix set by the routes. Set it to the IPs of the proxies in front of the "
                    + "gateway (e.g. the ingress controller).", TrustedProxies.PROPERTY);
            return (exchange, chain) -> chain.filter(exchange);
        }
        log.info("Accepting forwarded headers only from peers matching {}", trustedProxies);
        return new UntrustedForwardedHeadersFilter(TrustedProxies.from(trustedProxies));
    }
}
