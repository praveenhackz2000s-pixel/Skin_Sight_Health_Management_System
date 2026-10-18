# Smart Eye & Skin Specialist Hospital - container image.
# Built with plain javac: no Maven, no Gradle. The MySQL JDBC driver is a direct file download.

# ---------- stage 1: compile ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

ARG CONNECTOR_VERSION=8.4.0
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*
RUN mkdir -p lib \
 && curl -fSL -o lib/mysql-connector-j-${CONNECTOR_VERSION}.jar \
      https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${CONNECTOR_VERSION}/mysql-connector-j-${CONNECTOR_VERSION}.jar

COPY src ./src
RUN mkdir -p out \
 && find src/main/java -name "*.java" > sources.txt \
 && javac -encoding UTF-8 -d out -cp "lib/*" @sources.txt

# ---------- stage 2: run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home appuser
COPY --from=build /app/out ./out
COPY --from=build /app/lib ./lib
COPY src/main/resources ./resources
COPY schema.sql seed-demo.sql ./
USER appuser

# The hosting platform normally sets PORT; 8080 is the fallback.
ENV PORT=8080
EXPOSE 8080

# Settings come from environment variables at run time (never baked into the image):
#   DB_URL  DB_USER  DB_PASSWORD  APP_USER  APP_PASSWORD  [AUTO_INIT_DB  LOAD_DEMO_DATA]
CMD ["java", "-XX:MaxRAMPercentage=75", "-cp", "out:lib/*:resources", "com.hospital.Main"]
