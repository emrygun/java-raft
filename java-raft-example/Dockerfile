FROM openjdk:21-jdk-slim
WORKDIR /app

COPY target/jraft-example-*.jar app.jar

ARG RAFT_PORT=8080
ARG APP_PORT=7000
ENV RAFT_PORT=$RAFT_PORT
ENV APP_PORT=$APP_PORT

ENTRYPOINT ["java", "-jar", "app.jar"]