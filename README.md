# IGRP Cloud Gateway

A Spring Cloud Gateway implementation that can be used as a library in other projects. This gateway supports both Kubernetes and Eureka service discovery based on the active profile.

## Features

- Service registration and discovery using either Kubernetes or Eureka
- Profile-based configuration for development and production environments
- Can be used as a library in other projects

## Requirements

- Java 21
- Spring Boot 3.4.6
- Spring Cloud 2024.0.1

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

### Building the Docker Image Manually

You can build the Docker image manually with:

```bash
# Build the application
mvn clean package

# Build the Docker image
docker build -t igrp-cloud-gateway .
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

## License

[Your License Information]
