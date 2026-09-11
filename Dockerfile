# --- 1단계: 빌드 ---
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
# 프로젝트 Wrapper 버전을 사용하고 Windows 줄바꿈을 정리한다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew bootJar --no-daemon -x test

# --- 2단계: 실행 ---
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8082
CMD ["java", "-jar", "app.jar"]
