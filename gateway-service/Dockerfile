# --- Build stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the whole reactor so the parent POM's dependencyManagement resolves
COPY pom.xml .
COPY gateway-service gateway-service
COPY demo-services demo-services

RUN mvn -pl gateway-service -am -q clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/gateway-service/target/gateway-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
