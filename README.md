# IGRP Cloud Gateway

A Spring Cloud Gateway implementation that can be used as a library in other projects. This gateway supports both Kubernetes and Eureka service discovery based on the active profile.

## Overview

The IGRP Cloud Gateway serves as an API Gateway for microservices architecture, providing a single entry point for client applications to access various services. It handles routing, load balancing, and service discovery, making it easier to manage and scale microservices.

Key capabilities:
- Dynamic routing to microservices
- Load balancing between service instances
- Service discovery integration
- Profile-based configuration for different environments
- Centralized request handling and monitoring

## Features

- Service registration and discovery using either Kubernetes or Eureka
- Profile-based configuration for development and production environments
- Can be used as a library in other projects
- Automatic service registration with Eureka in development mode
- Kubernetes-native service discovery in production mode
- Actuator endpoints for monitoring and management (health probes only by default)
- Hardening: services' actuator endpoints are never proxied and spoofed forwarded headers are dropped
- OpenTelemetry observability: traces, metrics and logs exported over OTLP, with trace context propagated to downstream services
- Detailed logging for troubleshooting

## Requirements

- Java 21
- Spring Boot 3.5.16
- Spring Cloud 2025.0.3 (Spring Cloud Gateway 4.3.5)
- Netty 4.1.139.Final (overrides the version managed by Spring Boot to pick up security fixes)

## Usage

### Adding as a Dependency

Add the following dependency to your project's pom.xml:

```xml
<dependency>
    <groupId>cv.igrp.platform</groupId>
    <artifactId>igrp-cloud-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Configuration

The gateway supports two profiles:

1. **development** - Development environment using Eureka for service discovery
2. **production** - Production environment using Kubernetes for service discovery

#### Activating a Profile

To activate a profile, set the `spring.profiles.active` property:

```
# For development
spring.profiles.active=development

# For production
spring.profiles.active=production
```

You can set this property in your application.properties/application.yml file or as a command-line argument:

```
java -jar your-application.jar --spring.profiles.active=production
```

#### Configuration Files Structure

The gateway uses the following configuration files:

1. **application.yml** - Base configuration shared across all profiles
   - Application name and default profile
   - Actuator endpoints configuration
   - Logging settings

2. **application-development.yml** - Development-specific configuration
   - Eureka client configuration
   - Service discovery settings for development
   - Disables Kubernetes discovery

3. **application-production.yml** - Production-specific configuration
   - Disables Eureka client
   - Enables Kubernetes discovery
   - Kubernetes-specific settings

Here's a breakdown of key configuration properties:

##### Base Configuration (application.yml)
```yaml
spring:
  application:
    name: igrp-cloud-gateway  # Application name used for service registration
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:production}  # Default profile, can be overridden with env variable

management:
  endpoints:
    web:
      exposure:
        include: "health"  # Exposed actuator endpoints (see Security)
```

##### Development Configuration (application-development.yml)
```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVICE_URL:http://localhost:8761/eureka/}  # Eureka server URL
  instance:
    prefer-ip-address: true  # Register with IP address instead of hostname

spring:
  cloud:
    discovery:
      enabled: true  # Enable service discovery
    kubernetes:
      discovery:
        enabled: false  # Disable Kubernetes discovery in development
```

##### Production Configuration (application-production.yml)
```yaml
eureka:
  client:
    enabled: false  # Disable Eureka in production

spring:
  cloud:
    discovery:
      enabled: true  # Enable service discovery
    kubernetes:
      discovery:
        enabled: true  # Enable Kubernetes discovery
        all-namespaces: false  # Only discover services in the same namespace
```

### Development Profile (dev)

In development mode, the gateway uses Eureka for service discovery. Make sure your Eureka server is running and accessible at the URL specified in the configuration.

#### Testing with Docker Compose

A Docker Compose file is included to easily set up an Eureka server for testing:

```bash
# Start the Eureka server
docker-compose up -d

# Check that Eureka is running
# Access the Eureka dashboard at http://localhost:8761
```

The Eureka server will be available at http://localhost:8761, which matches the default configuration in the dev profile.

You can also run the gateway itself as a container by uncommenting the `igrp-gateway` service in the docker-compose.yml file:

```yaml
# Uncomment these lines in docker-compose.yml
igrp-gateway:
  build: .
  container_name: igrp-gateway
  ports:
    - "8080:8080"
  environment:
    - SPRING_PROFILES_ACTIVE=dev
    - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
  networks:
    - igrp-network
  depends_on:
    eureka-server:
      condition: service_healthy
```

First, build the application:

```bash
# Build the application
mvn clean package
```

Then run:

```bash
# Build and start both Eureka and the gateway
docker-compose up -d --build
```

The gateway will be available at http://localhost:8080 and will automatically register with Eureka.

### Production Profile (production)

In production mode, the gateway uses Kubernetes for service discovery. The application must be deployed in a Kubernetes cluster with the appropriate permissions to access the Kubernetes API.

## Docker Support

The project includes Docker support for easy testing and deployment:

- `Dockerfile` - Builds the gateway application as a container
- `.dockerignore` - Excludes unnecessary files from the Docker build context
- `docker-compose.yml` - Sets up Eureka and optionally the gateway for testing

### Docker Configuration Details

#### Dockerfile

The `Dockerfile` uses a multi-stage build process to create a lightweight container:

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /usr/src/service
COPY .mvn .mvn
COPY src src
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY pom.xml pom.xml
RUN chmod +x mvnw
RUN ./mvnw clean package

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache && apk add --no-cache curl
RUN addgroup -S -g 1000 igrp && adduser -S -D -H -u 1000 -G igrp igrp
WORKDIR /app
COPY --from=build /usr/src/service/target/igrp-cloud-gateway-0.0.1-SNAPSHOT-exec.jar ./igrp-cloud-gateway.jar
USER 1000:1000
EXPOSE 8080
CMD ["java", "-jar", "/app/igrp-cloud-gateway.jar"]
```

Key points:
- Uses Eclipse Temurin JRE 21 Alpine for a small footprint, with Alpine packages upgraded at build time
- Multi-stage build to minimize final image size; the build stage runs the tests
- Runs as the unprivileged user `igrp` (UID/GID 1000), compatible with Kubernetes `runAsNonRoot`
- Includes curl for health checks
- Exposes port 8080 for the gateway service
- With `readOnlyRootFilesystem: true`, mount a writable `emptyDir` at `/tmp` (used by the JVM and Netty)

#### Docker Compose

The `docker-compose.yml` file sets up a development environment with Eureka:

```yaml
services:
  eureka-server:
    image: steeltoeoss/eureka-server:latest
    container_name: eureka-server
    ports:
      - "8761:8761"
    environment:
      - EUREKA_CLIENT_REGISTER_WITH_EUREKA=false
      - EUREKA_CLIENT_FETCH_REGISTRY=false
    networks:
      - igrp-network
    healthcheck:
      test: [ "CMD", "curl", "-f", "http://localhost:8761/actuator/health" ]
      interval: 10s
      timeout: 5s
      retries: 3

  igrp-gateway:
    build: .
    container_name: igrp-gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=development
      - EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-server:8761/eureka/
    networks:
      - igrp-network

networks:
  igrp-network:
    driver: bridge
```

Key points:
- Sets up an Eureka server using the steeltoeoss/eureka-server image
- Configures the IGRP Gateway to use the development profile
- Creates a dedicated network for service communication
- Includes health checks for the Eureka server
- Maps ports to the host for easy access

### Building the Docker Image Manually

You can build the Docker image manually with:

```bash
# Build the application
mvn clean package

# Build the Docker image
docker build -t igrp-cloud-gateway .
```

### Environment Variables

When running the Docker container, you can configure the gateway using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile (development/production) | production |
| `EUREKA_SERVICE_URL` | URL for Eureka server | http://localhost:8761/eureka/ |
| `GATEWAY_LOG_LEVEL` | Log level of the routing/discovery loggers (`DEBUG` only to troubleshoot) | INFO |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | Actuator endpoints exposed over HTTP | health |
| `MANAGEMENT_SERVER_PORT` | Serve actuator on a separate port | (same as server) |
| `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTEDPROXIES` | Regex of proxy IPs allowed to send forwarded headers | private ranges |
| `IGRP_GATEWAY_BLOCKED_PATH_SEGMENTS` | Path segments never proxied to services | actuator |
| `OTEL_DISABLED` | Disable the OpenTelemetry SDK (`false` to export telemetry) | true |
| `OTEL_COLLECTOR_ENDPOINT` | OTLP/HTTP collector endpoint | http://localhost:4318 |
| `OTEL_TRACES_EXPORTER` / `OTEL_METRICS_EXPORTER` / `OTEL_LOGS_EXPORTER` | Exporter per signal (`otlp`, `console`, `none`) | otlp |

Example:
```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=development \
  -e EUREKA_SERVICE_URL=http://my-eureka:8761/eureka/ \
  -e GATEWAY_LOG_LEVEL=INFO \
  igrp-cloud-gateway
```

## Security

The gateway does not authenticate requests itself — each service validates its own tokens. Because anything the gateway exposes is public, it ships locked down:

### Gateway actuator

Only `/actuator/health` (and the `/liveness` / `/readiness` probes) is exposed, without details, and every endpoint is capped at read-only. The gateway endpoint that could create or refresh routes (`POST /actuator/gateway/routes/{id}`) is unreachable. To inspect routes, either expose more endpoints or — preferably — move actuator to a port that is not published by the Service/Ingress:

```bash
MANAGEMENT_SERVER_PORT=8082
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,gateway
MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS=always
```

With a separate management port, point the Kubernetes liveness/readiness probes to it. Keep `MANAGEMENT_ENDPOINTS_ACCESS_MAXPERMITTED=read-only` unless you really need write operations, and never expose those on a public port.

### Services' actuator endpoints

Discovery routes forward `/{service}/**` to each service, which would make every service's actuator public (`/igrp-access-management/actuator/heapdump`, `/env`, ...). The gateway answers 404 to any request whose path contains an `actuator` segment below the root, including encoded or obfuscated variants (`%61ctuator`, `actuator;x=1`, `//actuator`, `a/../actuator`). The list of blocked segments is configurable with `IGRP_GATEWAY_BLOCKED_PATH_SEGMENTS` (comma-separated). Services should still restrict their own actuator exposure — this is defence in depth.

### Forwarded headers

Services rely on `X-Forwarded-For/Host/Proto/Port/Prefix` for client IPs and to build absolute URLs (OIDC issuer, redirects, e-mail links), so only the proxies in front of the gateway may set them. Trusted proxies are a Java regex on the peer IP (`spring.cloud.gateway.server.webflux.trusted-proxies`). The default trusts the private ranges (`10/8`, `172.16/12`, `192.168/16`, loopback, IPv6 ULA), where the ingress controller and pods live; narrow it to the ingress controller's pod network:

```bash
SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTEDPROXIES='10\.42\.\d{1,3}\.\d{1,3}'
```

- **Trusted peer** — `X-Forwarded-*` headers are forwarded exactly as received; the gateway appends nothing. The proxy in front must therefore overwrite, not append to, any `X-Forwarded-For` sent by the client (ingress-nginx does so unless `use-forwarded-headers` is enabled).
- **Any other peer** — `Forwarded`, `X-Forwarded-*` and `X-Real-IP` are stripped, including the `X-Forwarded-Prefix` set by the discovery routes.
- A `Forwarded` (RFC 7239) header is never forwarded: Spring services prefer it over `X-Forwarded-*`.
- Headers set by the routes (the production discovery routes set `X-Forwarded-Prefix: /{serviceId}`) replace the proxy's value.

Do not set `trusted-proxies` to an empty value: Spring Cloud Gateway then removes every `X-Forwarded-*` header from all requests, which breaks the services' prefix and host handling. The gateway's own `Forwarded` / `X-Forwarded-*` writers are disabled in `application.yml` because they would drop the client IP from `X-Forwarded-For` and turn the routes' prefix into `/svc,/svc`.

## Observability (OpenTelemetry)

The gateway uses the [OpenTelemetry Spring Boot starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/), the same setup as iGRP Access Management. When enabled it exports, over OTLP:

- **Traces** — a server span for every request hitting the gateway. The W3C `traceparent`/`tracestate`/`baggage` headers are injected into each proxied request (`TraceContextPropagationHeadersFilter`), so downstream services continue the same trace with the gateway span as parent. An incoming `traceparent` from the caller is honoured.
- **Metrics** — Micrometer meters (JVM, HTTP server, gateway, Reactor Netty…) bridged to OTLP.
- **Logs** — Logback events shipped through the OTLP appender. Console lines are prefixed with `[service,traceId,spanId]` for correlation.

The SDK is **disabled by default**. Enable it with:

```bash
OTEL_DISABLED=false
OTEL_COLLECTOR_ENDPOINT=http://otel-collector:4318
```

Any standard `otel.*` property (e.g. `otel.traces.sampler`, `otel.resource.attributes`) can be set in `application.yml` or via the matching `OTEL_*` environment variable. The resource carries `service.name=igrp-cloud-gateway` and `deployment.environment=<active profile>`.

### Local stack

`docker-compose.yml` ships an opt-in Grafana stack (OTel collector, Tempo, Prometheus, Loki, Promtail, Grafana) under the `observability` profile, configured in `monitoring/`:

```bash
OTEL_DISABLED=false docker compose --profile observability up -d --build
```

Grafana runs at http://localhost:3000 (admin/admin) with the Tempo, Prometheus and Loki datasources provisioned. When running the gateway outside Docker, point it at `OTEL_COLLECTOR_ENDPOINT=http://localhost:4318`.

## Customization

You can customize the gateway by overriding the properties in your own application.properties or application.yml file.

### Example: Custom Routes

```properties
# Define custom routes
spring.cloud.gateway.routes[0].id=example-service
spring.cloud.gateway.routes[0].uri=lb://example-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/example/**
```

## Accessing Swagger UI JSON

The API Gateway provides access to the Swagger UI JSON documentation for all registered microservices. This allows you to explore and test the APIs of your services through the gateway.

### How to Access Swagger UI JSON

To access the Swagger UI JSON documentation for a service, use the following URL pattern:

```
https://{gateway-url}/{service-name}/v3/api-docs
```

Where:
- `{gateway-url}` is the base URL of your API Gateway
- `{service-name}` is the name of the service as registered with the gateway

### Example

For a service named `cadastro-service` and a gateway deployed at `api-gateway.example.com`:

```
https://api-gateway.example.com/cadastro-service/v3/api-docs
```

This will return the OpenAPI 3.0 JSON specification for the service, which can be imported into tools like Swagger UI, Postman, or other API documentation viewers.

### Using with Swagger UI

You can also view the API documentation directly in Swagger UI by accessing:

```
https://{gateway-url}/{service-name}/swagger-ui.html
```

### Notes

- The service must have SpringDoc OpenAPI configured to expose the API documentation
- The gateway must be configured to route requests to the service correctly
- Access to the API documentation may be subject to the same authentication and authorization rules as the service itself

## Configuration Validation

The gateway includes a configuration validator that runs at startup to ensure your gateway is properly configured. The validator checks:

1. **Active Profiles**: Verifies that the active profiles are properly set
2. **Service Discovery**: Ensures that the appropriate service discovery mechanism is enabled based on the active profile
   - For development: Eureka should be configured
   - For production: Kubernetes discovery should be enabled
3. **Routes Configuration**: Validates that each route has the required properties:
   - A unique ID
   - A valid URI
   - At least one predicate to match requests

If any issues are found, warnings will be logged to help you identify and fix the problems.

### Example Log Output

```
INFO: Validating API Gateway configuration...
INFO: Active profiles: [development]
INFO: Found 1 route(s):
INFO: Validating route: example-service
INFO: Route 'example-service' uses load balancing (lb://) for service: example-service
INFO: Route 'example-service' predicates: Path=/api/example/**
INFO: API Gateway configuration validation completed successfully.
```

## Troubleshooting

### Common Issues

#### Service Not Registering with Eureka in Development Mode

**Symptom**: The gateway service doesn't appear in the Eureka dashboard when running in development mode.

**Cause**: This can happen if service discovery is disabled in the configuration.

**Solution**: Ensure that in `application-development.yml`, the following properties are set correctly:
```yaml
spring:
  cloud:
    discovery:
      enabled: true  # Must be true for Eureka registration
    kubernetes:
      discovery:
        enabled: false  # Should be false in development mode
```

#### Cannot Connect to Eureka Server

**Symptom**: The application logs show connection errors to Eureka server.

**Solution**:
1. Verify that the Eureka server is running and accessible
2. Check the Eureka URL in `application-development.yml`
3. If running with Docker Compose, ensure the network configuration is correct
4. Try accessing the Eureka dashboard directly in a browser

#### Service Discovery Not Working in Production

**Symptom**: Services cannot be discovered when running in Kubernetes.

**Solution**:
1. Verify that the application is running with the production profile
2. Ensure the service account has permissions to access the Kubernetes API
3. Check that services have the correct labels for discovery
4. Verify network policies allow the gateway to access other services

### Logging

To enable detailed logging for troubleshooting, you can set the log level in `application.yml` or via environment variables:

```yaml
logging:
  level:
    root: DEBUG
    cv.igrp.platform: DEBUG
    org.springframework.cloud.gateway: DEBUG
    reactor.netty.http.server: DEBUG
```

Or using environment variables:
```
GATEWAY_LOG_LEVEL=DEBUG
```
## License

[Your License Information]
