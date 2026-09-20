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

RUN ./mvnw -pl transaction-service -am dependency:go-offline -B

COPY transaction-service/src transaction-service/src
RUN ./mvnw -pl transaction-service -am clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --uid 1001 spring
USER spring
WORKDIR /app
COPY --from=build /workspace/transaction-service/target/transaction-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
