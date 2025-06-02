package cv.igrp.platform.igrpcloudgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for IGRP Cloud Gateway.
 * This gateway can be used as a library in other projects and supports both
 * Kubernetes and Eureka service discovery based on the active profile.
 * <p>
 * Use the 'development' profile for local development with Eureka.
 * Use the 'production' profile for production deployment with Kubernetes.
 */
@SpringBootApplication
public class IgrpCloudGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgrpCloudGatewayApplication.class, args);
    }
}
