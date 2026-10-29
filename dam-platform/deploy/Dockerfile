# 通用后端服务镜像
# 使用方式：docker build 时通过 --build-arg JAR_FILE=dam-auth/target/dam-auth-1.0.0-exec.jar 指定各服务 jar
FROM eclipse-temurin:21-jre

ARG JAR_FILE
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseParallelGC"
ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Asia/Shanghai

WORKDIR /app
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
