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
- Actuator endpoints for monitoring and management
- Detailed logging for troubleshooting

## Requirements

- Java 21
- Spring Boot 3.4.6
- Spring Cloud 2025.0.0

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
        include: "health,info,gateway,routes"  # Exposed actuator endpoints
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
FROM eclipse-temurin:21-jdk-alpine
RUN apk add --no-cache curl
COPY --from=build /usr/src/service/target/igrp-cloud-gateway-0.0.1-SNAPSHOT-exec.jar ./igrp-cloud-gateway.jar
EXPOSE 8081
CMD ["java", "-jar", "./igrp-cloud-gateway.jar"]
```

Key points:
- Uses Eclipse Temurin JDK 21 Alpine for a small footprint
- Multi-stage build to minimize final image size
- Includes curl for health checks
- Exposes port 8081 for the gateway service

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
| `GATEWAY_LOG_LEVEL` | Log level for the application | DEBUG |

Example:
```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=development \
  -e EUREKA_SERVICE_URL=http://my-eureka:8761/eureka/ \
  -e GATEWAY_LOG_LEVEL=INFO \
  igrp-cloud-gateway
```

## Customization

You can customize the gateway by overriding the properties in your own application.properties or application.yml file.

### Example: Custom Routes

```properties
# Define custom routes
spring.cloud.gateway.routes[0].id=example-service
spring.cloud.gateway.routes[0].uri=lb://example-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/example/**
```

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

## API Client Configuration

### Handling Redirects in API Clients

When testing endpoints through the gateway, you may encounter issues with HTTP redirects. The gateway redirects requests to the appropriate backend services, but by default, many API clients change the HTTP method to GET when following redirects, regardless of the original method used (POST, PUT, DELETE, etc.).

#### Postman Configuration

In Postman, you need to enable the "Follow original HTTP Method" option:

1. Open Postman and go to Settings (or press Ctrl+,)
2. Navigate to the "General" tab
3. Under "Request", find and enable the option "Follow original HTTP Method for redirects"
4. Save your settings

#### Insomnia Configuration

In Insomnia, you need to enable a similar option:

1. Open Insomnia and go to Application Settings
2. Navigate to the "General" tab
3. Enable the option "Follow redirects with the original HTTP method"
4. Save your settings

### Understanding 403 Responses

If you receive a 403 Forbidden response when testing through the gateway, this may actually indicate a correct response depending on your authentication status. The gateway enforces security policies, and a 403 response often means that:

1. The request reached the correct service
2. The authentication was processed correctly
3. The user does not have sufficient permissions for the requested resource

Always check your authentication credentials and permissions when encountering 403 responses before assuming there's an issue with the gateway configuration.

## License

[Your License Information]