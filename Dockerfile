# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Resolved before the sources are copied so this layer is reused until pom.xml changes
RUN ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
# Tests run in CI before the image is built
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 flashquiz
WORKDIR /app
COPY --from=build /workspace/target/flashquiz-ai-*.jar app.jar
USER flashquiz
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
