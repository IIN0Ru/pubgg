# 펍지지 배포용 이미지. 두 단계로 만든다.
#   1단계(build): Maven 으로 실행 파일(jar)을 만든다. 이 단계의 도구들은 최종 이미지에 들어가지 않는다.
#   2단계(run)  : 만든 jar 와 Java 실행기(JRE)만 담아서 이미지를 가볍게 한다.

# ---------- 1단계: 빌드 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# pom.xml 만 먼저 복사해서 라이브러리를 받아 둔다.
# 소스만 바뀐 다음 빌드에서는 이 층이 캐시되어 라이브러리를 다시 받지 않는다.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# 테스트는 GitHub Actions(test.yml)에서 돌리므로 이미지를 만들 때는 건너뛴다
RUN mvn -B -ntp -DskipTests package

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 서버 프로그램을 root 권한으로 돌리지 않는다 (뚫렸을 때 피해를 줄이려고)
RUN useradd --system --no-create-home appuser

COPY --from=build /app/target/*.jar app.jar
USER appuser

# 운영 설정(application-prod.properties)을 쓴다
ENV SPRING_PROFILES_ACTIVE=prod
# 컨테이너에 배정된 메모리의 75%까지만 Java 가 쓰게 한다 (무료 플랜의 작은 메모리에서 강제 종료되지 않게)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# 실제 포트는 호스팅이 환경 변수 PORT 로 정해준다 (없으면 8080). EXPOSE 는 문서 역할이다
EXPOSE 8080

# DB_URL, DB_USER, DB_PASSWORD, PUBG_API_KEY 는 이미지에 넣지 않고 호스팅의 환경 변수로 넣는다
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
