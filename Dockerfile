FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/nexgate-*.jar nexgate.jar
COPY config/ ./config/

EXPOSE 8080

ENV NEXGATE_CONFIG=config/routes.yml
ENV REDIS_URL=redis://localhost:6379
ENV JWT_SECRET=ChangeMeInProductionUseEnvFile

ENTRYPOINT ["java", "-jar", "nexgate.jar"]
