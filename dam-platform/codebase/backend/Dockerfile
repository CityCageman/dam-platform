# 后端服务通用镜像（构建上下文：codebase/backend）
# 兼容既有 docker-compose*.yml 的 build（context: ../codebase/backend, dockerfile: Dockerfile）
FROM eclipse-temurin:21-jre

ARG JAR_FILE
ARG APP_NAME=dam-service

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseParallelGC -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai" \
    SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Shanghai \
    APP_NAME=${APP_NAME}

WORKDIR /app
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
