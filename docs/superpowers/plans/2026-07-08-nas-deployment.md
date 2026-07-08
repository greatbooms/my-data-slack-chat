# NAS Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synology NAS에서 이 Spring Boot 앱을 KIS 프로젝트와 같은 GHCR, Tailscale, SSH, Docker Compose 흐름으로 배포한다.

**Architecture:** 로컬 개발 Compose는 유지하고, NAS 전용 배포 파일을 `deploy/`, `scripts/`, `.github/workflows/`에 분리한다. 앱 이미지는 관리자 UI와 Spring Boot jar를 함께 포함하며, 기존 NAS DB는 JDBC URL 환경변수로 연결한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Gradle, Node 22, Docker, Docker Compose, GHCR, GitHub Actions, Tailscale, Synology NAS.

## Global Constraints

- 사용자-facing 답변과 문서는 한국어로 작성한다.
- 실제 비밀값은 커밋하지 않는다.
- `.env`는 커밋하지 않고 예시는 `.env.prod.example`에만 둔다.
- 로컬 `docker-compose.yml`은 PostgreSQL 개발 DB 용도로 유지한다.
- 운영 DB URL은 Spring JDBC 형식인 `jdbc:postgresql://...`을 사용한다.

---

### Task 1: 배포 동작 테스트 추가

**Files:**
- Create: `src/test/java/com/mydata/deployment/NasDeploymentArtifactsTest.java`
- Create: `src/test/java/com/mydata/security/ActuatorHealthEndpointTest.java`

**Interfaces:**
- Consumes: 현재 Spring Boot 테스트 컨텍스트와 `PostgresIntegrationTest`
- Produces: 배포 산출물 파일과 `/actuator/health` 접근 계약

- [ ] **Step 1: Write the failing deployment artifact test**

Create `src/test/java/com/mydata/deployment/NasDeploymentArtifactsTest.java` with assertions that `Dockerfile`, `.dockerignore`, `deploy/compose.yml`, `scripts/deploy-synology.sh`, `.github/workflows/deploy.yml`, `.env.prod.example`, and `docs/nas-deployment.md` exist and contain the expected NAS deployment markers.

- [ ] **Step 2: Write the failing health endpoint test**

Create `src/test/java/com/mydata/security/ActuatorHealthEndpointTest.java` and assert anonymous `GET /actuator/health` returns HTTP 200 with `$.status == "UP"`.

- [ ] **Step 3: Run tests to verify red**

Run: `./gradlew test --tests com.mydata.deployment.NasDeploymentArtifactsTest --tests com.mydata.security.ActuatorHealthEndpointTest`

Expected: FAIL because deployment files and Actuator endpoint do not exist yet.

### Task 2: Docker and Synology deployment files

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `deploy/compose.yml`
- Create: `scripts/deploy-synology.sh`
- Create: `.github/workflows/deploy.yml`
- Create: `.env.prod.example`

**Interfaces:**
- Consumes: Spring Boot jar build task and admin UI npm build
- Produces: NAS deployable GHCR image and remote Compose deployment flow

- [ ] **Step 1: Add Dockerfile**

Add a multi-stage Dockerfile that builds admin UI with Node 22, builds the Spring Boot jar with Java 21, copies the jar into a Java 21 JRE image, exposes port `50506`, and runs `java -jar /app/app.jar`.

- [ ] **Step 2: Add Docker ignore rules**

Add `.dockerignore` to exclude local build output, VCS metadata, `.env` files, node modules, admin dist, and Playwright artifacts.

- [ ] **Step 3: Add Synology Compose file**

Add `deploy/compose.yml` with `image: ${IMAGE:-ghcr.io/greatbooms/my-data-slack-chat:latest}`, `container_name: my-data-slack-chat`, `network_mode: host`, `env_file: .env.prod`, log rotation, and `/actuator/health` healthcheck.

- [ ] **Step 4: Add Synology deploy script**

Add `scripts/deploy-synology.sh` based on the KIS script, with app name default `my-data-slack-chat`, runtime env check, image pull, Compose up, health polling, and stale image cleanup.

- [ ] **Step 5: Add GitHub Actions workflow**

Add `.github/workflows/deploy.yml` to build and push the image to GHCR, connect through Tailscale, upload deploy files, and run the Synology deploy script over SSH.

- [ ] **Step 6: Add production env example**

Add `.env.prod.example` with JDBC `DATABASE_URL`, separated `DATABASE_USERNAME` and `DATABASE_PASSWORD`, admin bootstrap values, Slack/Notion values, and LLM provider values.

### Task 3: Spring Boot health endpoint

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Consumes: Spring Boot Actuator
- Produces: Anonymous `/actuator/health` endpoint for Docker healthcheck

- [ ] **Step 1: Add Actuator dependency**

Add `implementation 'org.springframework.boot:spring-boot-starter-actuator'` to `build.gradle`.

- [ ] **Step 2: Configure health exposure**

Add `management.endpoints.web.exposure.include=health` and keep health details hidden in `src/main/resources/application.yml`.

- [ ] **Step 3: Run focused tests**

Run: `./gradlew test --tests com.mydata.deployment.NasDeploymentArtifactsTest --tests com.mydata.security.ActuatorHealthEndpointTest`

Expected: PASS.

### Task 4: Deployment documentation

**Files:**
- Create: `docs/nas-deployment.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: deployment files from Task 2
- Produces: User-facing NAS setup instructions

- [ ] **Step 1: Add NAS deployment guide**

Document the KIS-like deployment flow, `.env.prod` example location, required GitHub secrets, NAS Docker prerequisites, DB URL conversion from `postgresql://...` to `jdbc:postgresql://...`, and manual deployment commands.

- [ ] **Step 2: Link from README**

Add a short NAS deployment section in `README.md` linking to `docs/nas-deployment.md`.

- [ ] **Step 3: Run full verification**

Run: `./gradlew test`.

Expected: PASS.

- [ ] **Step 4: Build Docker image**

Run: `docker build -t my-data-slack-chat:test .`.

Expected: PASS.
