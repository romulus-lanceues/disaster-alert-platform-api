#!/bin/bash
set -euxo pipefail

# 1. Update and install Docker, Compose v2, curl, unzip, python3, and cron non-interactively
export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a

APT_OPTS=(
  -y
  -o DPkg::Lock::Timeout=120
  -o Dpkg::Options::="--force-confdef"
  -o Dpkg::Options::="--force-confold"
)

apt-get update "${APT_OPTS[@]}" && apt-get upgrade "${APT_OPTS[@]}"
apt-get install "${APT_OPTS[@]}" docker.io docker-compose-v2 cron curl unzip python3

systemctl enable --now docker cron

# 2. Install official AWS CLI v2 in an isolated, private temporary directory with --update for idempotency
AWS_TMP_DIR=$(umask 077 && mktemp -d /tmp/awscliv2.XXXXXX)
trap 'rm -rf "$AWS_TMP_DIR"' EXIT

curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "$AWS_TMP_DIR/awscliv2.zip"
unzip -q "$AWS_TMP_DIR/awscliv2.zip" -d "$AWS_TMP_DIR"
"$AWS_TMP_DIR/aws/install" --update
rm -rf "$AWS_TMP_DIR"
trap - EXIT

# 3. 2 GB swap as an out-of-memory safety net on a 2 GB instance (idempotent guards)
if [[ ! -f /swapfile ]]; then
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
fi

if ! swapon --show | grep -q '/swapfile'; then
  swapon /swapfile
fi

if ! grep -qxF '/swapfile none swap sw 0 0' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

mkdir -p /opt/tanaw/bundle /opt/tanaw/state /opt/tanaw/import
chmod 700 /opt/tanaw

# 4. Nightly DB backup at 02:00 PHT (18:00 UTC)
cat << 'EOF' > /etc/cron.d/tanaw-backup
PATH=/usr/local/bin:/usr/bin:/bin
0 18 * * * root /bin/bash /opt/tanaw/bundle/backup.sh >> /var/log/tanaw-backup.log 2>&1
EOF
chmod 0644 /etc/cron.d/tanaw-backup
