# syntax=docker/dockerfile:1

FROM node:22-alpine AS admin-ui-builder
WORKDIR /workspace/frontend/admin
COPY frontend/admin/package.json frontend/admin/package-lock.json ./
RUN npm ci
COPY src/main/resources/graphql/admin.graphqls /workspace/src/main/resources/graphql/admin.graphqls
COPY frontend/admin/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS app-builder
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
COPY frontend/admin ./frontend/admin
COPY --from=admin-ui-builder /workspace/frontend/admin/dist ./frontend/admin/dist
RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon bootJar -x test -x adminUiNpmInstall -x adminUiNpmBuild

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=app-builder /workspace/build/libs/*.jar /app/app.jar
USER spring:spring
EXPOSE 50506
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
