# --- 1단계: 빌드 ---
FROM gradle:8-jdk21-alpine AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon -x test

# --- 2단계: 실행 ---
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8082
CMD ["java", "-jar", "app.jar"]