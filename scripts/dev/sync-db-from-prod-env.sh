#!/usr/bin/env sh
set -eu

SOURCE_ENV_FILE="${SOURCE_ENV_FILE:-.env.prod}"
TARGET_ENV_FILE="${TARGET_ENV_FILE:-.env}"
PG_DUMP_BIN="${PG_DUMP_BIN:-pg_dump}"
PSQL_BIN="${PSQL_BIN:-psql}"
PG_RESTORE_BIN="${PG_RESTORE_BIN:-pg_restore}"
KEEP_DUMP="${KEEP_DUMP:-false}"

YES="false"
DRY_RUN="false"
ALLOW_NON_LOCAL_TARGET="false"

usage() {
  cat <<'EOF'
Usage: scripts/dev/sync-db-from-prod-env.sh [options]

Copies the DB configured by .env.prod into the DB configured by .env.
The target schema is dropped and recreated before restore.

Options:
  --yes                         Required to overwrite the target DB schema.
  --dry-run                     Print the parsed source/target plan only.
  --allow-non-local-target      Allow target DB hosts other than localhost/127.0.0.1.
  --source-env <path>           Source env file. Default: .env.prod
  --target-env <path>           Target env file. Default: .env
  -h, --help                    Show this help.

Environment overrides:
  PG_DUMP_BIN, PSQL_BIN, PG_RESTORE_BIN, KEEP_DUMP
EOF
}

fail() {
  echo "[error] $*" >&2
  exit 2
}

info() {
  echo "[info] $*"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --yes)
      YES="true"
      shift
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    --allow-non-local-target)
      ALLOW_NON_LOCAL_TARGET="true"
      shift
      ;;
    --source-env)
      [ "$#" -ge 2 ] || fail "--source-env requires a path"
      SOURCE_ENV_FILE="$2"
      shift 2
      ;;
    --target-env)
      [ "$#" -ge 2 ] || fail "--target-env requires a path"
      TARGET_ENV_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

read_env_value() {
  file="$1"
  key="$2"

  awk -v key="$key" '
    /^[[:space:]]*($|#)/ { next }
    {
      line = $0
      sub(/\r$/, "", line)
      sub(/^[[:space:]]*export[[:space:]]+/, "", line)
      split(line, parts, "=")
      candidate = parts[1]
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", candidate)

      if (candidate == key) {
        sub(/^[^=]*=/, "", line)
        sub(/[[:space:]]+#.*$/, "", line)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        if ((line ~ /^".*"$/) || (line ~ /^'\''.*'\''$/)) {
          line = substr(line, 2, length(line) - 2)
        }
        print line
        exit
      }
    }
  ' "$file"
}

require_file() {
  file="$1"
  [ -f "$file" ] || fail "Missing env file: $file"
}

require_value() {
  file="$1"
  key="$2"
  value="$3"
  [ -n "$value" ] || fail "Missing ${key} in ${file}"
}

query_param_value() {
  query="$1"
  key="$2"

  printf '%s\n' "$query" | tr '&' '\n' | awk -F= -v key="$key" '$1 == key { print $2; exit }'
}

validate_identifier() {
  value="$1"
  label="$2"

  printf '%s' "$value" | grep -Eq '^[A-Za-z_][A-Za-z0-9_]*$' \
    || fail "${label} must be a PostgreSQL identifier: ${value}"
}

parse_database_url() {
  prefix="$1"
  url="$2"

  case "$url" in
    jdbc:postgresql://*)
      rest="${url#jdbc:postgresql://}"
      ;;
    postgresql://*)
      rest="${url#postgresql://}"
      case "$rest" in
        *@*) rest="${rest#*@}" ;;
      esac
      ;;
    *)
      fail "Unsupported ${prefix} DATABASE_URL. Use jdbc:postgresql://... or postgresql://..."
      ;;
  esac

  case "$rest" in
    *\?*)
      base="${rest%%\?*}"
      query="${rest#*\?}"
      ;;
    *)
      base="$rest"
      query=""
      ;;
  esac

  host_port="${base%%/*}"
  database="${base#*/}"
  [ "$database" != "$base" ] || fail "${prefix} DATABASE_URL must include a database name"
  [ -n "$database" ] || fail "${prefix} DATABASE_URL must include a database name"

  case "$host_port" in
    *:*)
      host="${host_port%:*}"
      port="${host_port##*:}"
      ;;
    *)
      host="$host_port"
      port="5432"
      ;;
  esac

  [ -n "$host" ] || fail "${prefix} DATABASE_URL must include a host"
  [ -n "$port" ] || fail "${prefix} DATABASE_URL must include a port"

  schema="$(query_param_value "$query" "currentSchema")"
  if [ -z "$schema" ]; then
    schema="$(query_param_value "$query" "schema")"
  fi
  if [ -z "$schema" ]; then
    schema="public"
  fi

  validate_identifier "$schema" "${prefix} schema"

  eval "${prefix}_HOST=\$host"
  eval "${prefix}_PORT=\$port"
  eval "${prefix}_DATABASE=\$database"
  eval "${prefix}_SCHEMA=\$schema"
}

normalize_host() {
  case "$1" in
    localhost|127.0.0.1|"::1"|"[::1]")
      printf '%s\n' "localhost"
      ;;
    *)
      printf '%s\n' "$1"
      ;;
  esac
}

is_local_host() {
  [ "$(normalize_host "$1")" = "localhost" ]
}

load_config() {
  require_file "$SOURCE_ENV_FILE"
  require_file "$TARGET_ENV_FILE"

  SOURCE_URL="$(read_env_value "$SOURCE_ENV_FILE" "DATABASE_URL")"
  SOURCE_USERNAME="$(read_env_value "$SOURCE_ENV_FILE" "DATABASE_USERNAME")"
  SOURCE_PASSWORD="$(read_env_value "$SOURCE_ENV_FILE" "DATABASE_PASSWORD")"
  TARGET_URL="$(read_env_value "$TARGET_ENV_FILE" "DATABASE_URL")"
  TARGET_USERNAME="$(read_env_value "$TARGET_ENV_FILE" "DATABASE_USERNAME")"
  TARGET_PASSWORD="$(read_env_value "$TARGET_ENV_FILE" "DATABASE_PASSWORD")"

  require_value "$SOURCE_ENV_FILE" "DATABASE_URL" "$SOURCE_URL"
  require_value "$SOURCE_ENV_FILE" "DATABASE_USERNAME" "$SOURCE_USERNAME"
  require_value "$SOURCE_ENV_FILE" "DATABASE_PASSWORD" "$SOURCE_PASSWORD"
  require_value "$TARGET_ENV_FILE" "DATABASE_URL" "$TARGET_URL"
  require_value "$TARGET_ENV_FILE" "DATABASE_USERNAME" "$TARGET_USERNAME"
  require_value "$TARGET_ENV_FILE" "DATABASE_PASSWORD" "$TARGET_PASSWORD"

  parse_database_url "SOURCE" "$SOURCE_URL"
  parse_database_url "TARGET" "$TARGET_URL"

  [ "$SOURCE_SCHEMA" = "$TARGET_SCHEMA" ] \
    || fail "Source and target schema must match. source=${SOURCE_SCHEMA}, target=${TARGET_SCHEMA}"
}

ensure_safe_target() {
  source_host="$(normalize_host "$SOURCE_HOST")"
  target_host="$(normalize_host "$TARGET_HOST")"

  if [ "$source_host" = "$target_host" ] \
    && [ "$SOURCE_PORT" = "$TARGET_PORT" ] \
    && [ "$SOURCE_DATABASE" = "$TARGET_DATABASE" ] \
    && [ "$SOURCE_SCHEMA" = "$TARGET_SCHEMA" ]; then
    fail "Source and target DB point to the same database/schema"
  fi

  if ! is_local_host "$TARGET_HOST" && [ "$ALLOW_NON_LOCAL_TARGET" != "true" ]; then
    fail "Target DB host is not local: ${TARGET_HOST}. Re-run with --allow-non-local-target only if this is intentional."
  fi
}

print_plan() {
  info "Source DB: ${SOURCE_HOST}:${SOURCE_PORT}/${SOURCE_DATABASE} schema=${SOURCE_SCHEMA} user=${SOURCE_USERNAME}"
  info "Target DB: ${TARGET_HOST}:${TARGET_PORT}/${TARGET_DATABASE} schema=${TARGET_SCHEMA} user=${TARGET_USERNAME}"
}

require_binary() {
  binary="$1"
  command -v "$binary" >/dev/null 2>&1 || fail "Required command not found: $binary"
}

run_source_pg() {
  PGPASSWORD="$SOURCE_PASSWORD" \
  PGHOST="$SOURCE_HOST" \
  PGPORT="$SOURCE_PORT" \
  PGDATABASE="$SOURCE_DATABASE" \
  PGUSER="$SOURCE_USERNAME" \
    "$@"
}

run_target_pg() {
  PGPASSWORD="$TARGET_PASSWORD" \
  PGHOST="$TARGET_HOST" \
  PGPORT="$TARGET_PORT" \
  PGDATABASE="$TARGET_DATABASE" \
  PGUSER="$TARGET_USERNAME" \
    "$@"
}

sync_database() {
  require_binary "$PG_DUMP_BIN"
  require_binary "$PSQL_BIN"
  require_binary "$PG_RESTORE_BIN"

  DUMP_FILE="$(mktemp "${TMPDIR:-/tmp}/my-data-db-sync.XXXXXX.dump")"
  cleanup() {
    if [ "$KEEP_DUMP" != "true" ]; then
      rm -f "$DUMP_FILE"
    else
      info "Keeping dump file: $DUMP_FILE"
    fi
  }
  trap cleanup EXIT INT TERM

  info "Dumping source schema"
  run_source_pg "$PG_DUMP_BIN" \
    --format=custom \
    --no-owner \
    --no-acl \
    --schema="$SOURCE_SCHEMA" \
    --file="$DUMP_FILE"

  reset_sql="DROP SCHEMA IF EXISTS \"${TARGET_SCHEMA}\" CASCADE; CREATE SCHEMA \"${TARGET_SCHEMA}\";"

  info "Resetting target schema"
  run_target_pg "$PSQL_BIN" \
    --set=ON_ERROR_STOP=1 \
    --dbname="$TARGET_DATABASE" \
    --command="$reset_sql"

  info "Restoring dump into target DB"
  run_target_pg "$PG_RESTORE_BIN" \
    --clean \
    --if-exists \
    --single-transaction \
    --no-owner \
    --no-acl \
    --dbname="$TARGET_DATABASE" \
    "$DUMP_FILE"

  info "Sync complete"
}

load_config
ensure_safe_target
print_plan

if [ "$DRY_RUN" = "true" ]; then
  info "Dry run only; no dump or restore was executed"
  exit 0
fi

if [ "$YES" != "true" ]; then
  fail "Refusing to overwrite target DB schema without --yes"
fi

sync_database
