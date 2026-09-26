FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S fisio && adduser -S fisio -G fisio
COPY --from=build /app/target/fisio-api-0.0.1-SNAPSHOT.jar app.jar
USER fisio
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
