FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /usr/src/service

COPY .mvn .mvn
COPY src src
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY pom.xml pom.xml

RUN chmod +x mvnw
RUN ./mvnw clean package

FROM eclipse-temurin:21-jre-alpine

# Pull the latest patched Alpine packages on top of the base image
RUN apk upgrade --no-cache && apk add --no-cache curl

# Run as an unprivileged user; numeric UID so Kubernetes runAsNonRoot can verify it
RUN addgroup -S -g 1000 igrp && adduser -S -D -H -u 1000 -G igrp igrp

WORKDIR /app

COPY --from=build /usr/src/service/target/igrp-cloud-gateway-0.0.1-SNAPSHOT-exec.jar ./igrp-cloud-gateway.jar

USER 1000:1000

EXPOSE 8080

CMD ["java", "-jar", "/app/igrp-cloud-gateway.jar"]
