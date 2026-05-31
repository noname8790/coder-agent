FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/coder-agent.jar /app/coder-agent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/coder-agent.jar"]
