# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 tanaw \
 && useradd --system --uid 10001 --gid tanaw --no-create-home tanaw
WORKDIR /app
COPY --from=builder /workspace/app.jar app.jar
USER tanaw:tanaw
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=128m -XX:MaxDirectMemorySize=64m -Xss512k -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
