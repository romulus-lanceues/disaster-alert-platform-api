#!/usr/bin/env bash
# Usage: deploy.sh <image-tag>
set -euo pipefail

umask 077
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"

TAG="${1:?usage: deploy.sh <image-tag>}"
if [[ ! "$TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$ ]]; then
  echo "Error: Invalid image tag '$TAG'" >&2
  exit 1
fi

REGION="ap-southeast-1"
ROOT="/opt/tanaw"
BUNDLE="$ROOT/bundle"
STATE="$ROOT/state"
install -d -m 700 "$ROOT" "$BUNDLE" "$STATE"

PREV=$(cat "$STATE/current_tag" 2>/dev/null || true)
if [[ -n "$PREV" && ! "$PREV" =~ ^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$ ]]; then
  PREV=""
fi
ACTIVE_TAG="${PREV:-$TAG}"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR_REGISTRY="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

# 1. Render secrets from Parameter Store into .env (root-only, never in git or GitHub)
trap 'rm -f "$ROOT/.env.tmp"' EXIT

aws ssm get-parameters-by-path --region "$REGION" --path /tanaw/prod/ --recursive --with-decryption --output json \
  | python3 -c '
import json, re, sys

data = json.load(sys.stdin)
params = data.get("Parameters", []) if isinstance(data, dict) else data

lines = []
for p in params:
    if isinstance(p, dict):
        name, val = p.get("Name", ""), p.get("Value", "")
    elif isinstance(p, (list, tuple)) and len(p) >= 2:
        name, val = p[0], p[1]
    else:
        continue

    key = name.rsplit("/", 1)[-1]
    if not re.match(r"^[A-Z0-9_]+$", key):
        sys.stderr.write(f"Invalid variable name: {key}\n")
        sys.exit(1)

    if "\n" in val or "\r" in val:
        sys.stderr.write(f"Parameter {key} contains illegal newline characters\n")
        sys.exit(1)

    escaped = val.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
    lines.append(f"{key}=\"{escaped}\"")

if not lines:
    sys.stderr.write("Error: Parameter Store returned no parameters for /tanaw/prod/\n")
    sys.exit(1)

for line in lines:
    print(line)
' > "$ROOT/.env.tmp"

printf 'ECR_REGISTRY="%s"\nIMAGE_TAG="%s"\n' "$ECR_REGISTRY" "$ACTIVE_TAG" >> "$ROOT/.env.tmp"

install -m 600 "$ROOT/.env.tmp" "$ROOT/.env"
rm -f "$ROOT/.env.tmp"
ln -sf "$ROOT/.env" "$BUNDLE/.env"
trap - EXIT

# 2. Pull the new image
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"
compose() { IMAGE_TAG="$1" docker compose -p tanaw -f "$BUNDLE/docker-compose.prod.yml" --env-file "$ROOT/.env" "${@:2}"; }

wait_for_readiness() {
  local attempts="$1"
  local i
  for i in $(seq 1 "$attempts"); do
    if curl --connect-timeout 2 --max-time 3 -fsS http://127.0.0.1:8080/actuator/health/readiness > /dev/null; then
      return 0
    fi
    if [[ "$i" -lt "$attempts" ]]; then
      sleep 5
    fi
  done
  return 1
}

compose "$TAG" pull app
compose "$TAG" up -d --remove-orphans

# 3. Wait for readiness (Flyway migrations run on startup), or roll back
if wait_for_readiness 36; then
  echo "$TAG" > "$STATE/current_tag"
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=\"$TAG\"/" "$ROOT/.env"
  compose "$TAG" exec -T caddy caddy reload --config /etc/caddy/Caddyfile
  docker image prune -af --filter "until=168h" > /dev/null || true
  echo "Deployed $TAG"
  exit 0
fi

echo "Readiness check failed for $TAG" >&2
compose "$TAG" logs --tail 150 app >&2 || true
if [[ -n "$PREV" && "$PREV" != "$TAG" ]]; then
  echo "Rolling back to $PREV" >&2
  compose "$PREV" up -d app
  if wait_for_readiness 24; then
    echo "$PREV" > "$STATE/current_tag"
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=\"$PREV\"/" "$ROOT/.env"
    echo "Rolled back to $PREV and verified readiness" >&2
  else
    echo "CRITICAL: Rollback to $PREV also failed readiness" >&2
    compose "$PREV" logs --tail 150 app >&2 || true
  fi
fi
exit 1
