FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/igrp-cloud-gateway-*.jar igrp-cloud-gateway.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "igrp-cloud-gateway.jar"]