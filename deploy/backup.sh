#!/usr/bin/env bash
set -euo pipefail

umask 077
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"

REGION="ap-southeast-1"
ENV_FILE="/opt/tanaw/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: $ENV_FILE does not exist" >&2
  exit 1
fi

# Safely extract only the variables needed for the backup
POSTGRES_USER=$( (grep -E '^POSTGRES_USER=' "$ENV_FILE" || true) | tail -n 1 | cut -d '=' -f2- | tr -d '"'\' | tr -d '\r')
POSTGRES_DB=$( (grep -E '^POSTGRES_DB=' "$ENV_FILE" || true) | tail -n 1 | cut -d '=' -f2- | tr -d '"'\' | tr -d '\r')
OPS_BUCKET=$( (grep -E '^OPS_BUCKET=' "$ENV_FILE" || true) | tail -n 1 | cut -d '=' -f2- | tr -d '"'\' | tr -d '\r')

if [[ -z "$POSTGRES_USER" || -z "$POSTGRES_DB" || -z "$OPS_BUCKET" ]]; then
  echo "Error: Missing required backup variables in $ENV_FILE" >&2
  exit 1
fi

TMP_DUMP=$(mktemp /tmp/tanaw-backup.XXXXXX.dump)
trap 'rm -f "$TMP_DUMP"' EXIT

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
docker exec tanaw-db-1 pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$TMP_DUMP"

if [[ ! -s "$TMP_DUMP" ]]; then
  echo "Error: pg_dump produced an empty or corrupted dump file" >&2
  exit 1
fi

aws s3 cp --region "$REGION" "$TMP_DUMP" "s3://$OPS_BUCKET/backups/tanaw-$STAMP.dump"
rm -f "$TMP_DUMP"
trap - EXIT

echo "Backup uploaded: tanaw-$STAMP.dump"

