# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY pom.xml .
COPY account-service/pom.xml account-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

RUN ./mvnw -pl api-gateway -am dependency:go-offline -B

COPY api-gateway/src api-gateway/src
RUN ./mvnw -pl api-gateway -am clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --uid 1001 spring
USER spring
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
