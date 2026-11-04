# ---- 1단계: 빌드 (Maven + JDK 21) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# pom 만 먼저 복사해서 라이브러리를 받아 둔다 (소스만 바뀌면 이 층은 캐시에서 재사용)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- 2단계: 실행 (JRE 만) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# 비밀번호·API 키는 이미지에 넣지 않는다. 실행할 때 환경 변수로만 받는다.
RUN useradd --system --no-create-home pubgg
COPY --from=build /build/target/pubgg-*.jar app.jar
USER pubgg
EXPOSE 8080
# 컨테이너에 걸린 메모리 제한의 75% 까지만 힙으로 쓴다 (작은 서버를 흉내 낼 때 mem_limit 와 함께 동작)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
