# 1단계: 빌드 환경 (Builder)
# Gradle과 JDK 17이 포함된 이미지를 기반으로 빌드합니다.
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Gradle 캐시를 활용하기 위해 빌드 설정 파일만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# gradlew 실행 권한 부여 (윈도우에서 넘어온 파일일 경우를 대비)
RUN chmod +x ./gradlew

# 의존성 다운로드 (소스코드 복사 전 미리 캐싱)
# --no-daemon: CI/CD 환경이나 Docker에서는 데몬을 안 쓰는 게 좋습니다.
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사 및 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 환경 (Runner)
# 실행에는 가벼운 JRE만 있으면 됩니다.
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 1단계에서 빌드된 JAR 파일만 가져옵니다.
COPY --from=builder /app/build/libs/*.jar app.jar

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]