FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:25-jre-ubi10-minimal

WORKDIR /app

RUN mkdir -p /app/backup && chown 10001:0 /app/backup

COPY --from=build --chown=10001:0 /workspace/target/gmx-backup-1.0.0.jar /app/gmx-backup.jar

ENV BACKUP_OUTPUT_DIRECTORY=/app/backup

VOLUME ["/app/backup"]

USER 10001

ENTRYPOINT ["java", "-jar", "/app/gmx-backup.jar"]
