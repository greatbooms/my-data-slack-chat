# NAS 배포 설계

## 목표

이 프로젝트를 KIS 프로젝트의 Synology NAS 배포 방식과 맞춰, `main` 브랜치 push 후 GHCR 이미지 빌드와 NAS Docker Compose 재기동까지 자동화한다.

## 기준

- 로컬 개발용 `docker-compose.yml`은 PostgreSQL 포함 구조로 유지한다.
- NAS 배포용 Compose는 별도 `deploy/compose.yml`로 분리한다.
- NAS에는 이미 PostgreSQL/pgvector 컨테이너가 있고, 앱 컨테이너는 `network_mode: host`로 NAS 호스트 포트의 DB에 접속한다.
- Spring Boot는 Prisma 형식 `postgresql://...`이 아니라 JDBC 형식 `jdbc:postgresql://...`을 사용한다.
- 실제 비밀값은 커밋하지 않고, 운영 서버의 `.env.prod`에만 둔다.

## 구조

- `Dockerfile`: 관리자 React UI를 Node stage에서 빌드하고, Spring Boot jar를 Java 21 stage에서 빌드한 뒤, JRE runtime 이미지로 실행한다.
- `deploy/compose.yml`: GHCR 이미지를 받아 `my-data-slack-chat` 컨테이너로 실행한다. KIS 프로젝트처럼 `network_mode: host`, `.env.prod`, json-file 로그 로테이션, Docker healthcheck를 사용한다.
- `scripts/deploy-synology.sh`: KIS 프로젝트의 NAS 배포 스크립트 패턴을 따른다. `.env.prod` 존재 여부를 확인하고, 이미지를 pull한 뒤 Compose up, health 대기, 사용하지 않는 이전 이미지를 정리한다.
- `.github/workflows/deploy.yml`: `main` push 또는 수동 실행 시 GHCR에 이미지를 push하고 Tailscale/SSH로 NAS에 배포 파일을 업로드한 뒤 원격 배포 스크립트를 실행한다.
- `.env.prod.example`: 운영용 환경변수 예시를 제공한다.
- `docs/nas-deployment.md`: 사용자가 NAS와 GitHub에서 직접 설정해야 하는 값을 문서화한다.

## Healthcheck

Spring Boot Actuator의 `/actuator/health`를 배포 healthcheck로 사용한다. 이 endpoint는 관리자 세션 없이 접근 가능해야 하며, 관리자 UI/GraphQL 보안 범위와 분리한다.

## 사용자가 해야 할 일

- NAS의 배포 경로에 `.env.prod`를 만들고 실제 DB/Slack/Notion/LLM 비밀값을 채운다.
- GitHub Actions secrets에 Tailscale, SSH, NAS 배포 경로 값을 등록한다.
- NAS에서 Docker CLI가 `/usr/local/bin/docker`가 아니면 `DOCKER_BIN`을 조정한다.
- NAS SSH 사용자가 passwordless sudo 없이 Docker를 실행할 수 없으면 `USE_SUDO_DOCKER` 또는 sudoers 설정을 맞춘다.

## 검증

- 배포 산출물 파일 구조 테스트
- `/actuator/health` 익명 접근 테스트
- 전체 Gradle 테스트
- Docker 이미지 빌드 검증
