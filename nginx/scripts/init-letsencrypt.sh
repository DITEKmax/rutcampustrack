#!/bin/bash
# init-letsencrypt.sh — Run ONCE on first deploy to issue SSL certificate.
# After this, the certbot sidecar in docker-compose.prod.yml handles renewals.
#
# Prerequisites:
#   - DNS A record pointing $DOMAIN to this server's public IP
#   - Ports 80 and 443 open in firewall
#   - .env.prod file with DOMAIN and CERTBOT_EMAIL set
#
# Usage: ./nginx/scripts/init-letsencrypt.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# Load env vars
if [ -f .env.prod ]; then
  export $(grep -E '^(DOMAIN|CERTBOT_EMAIL)=' .env.prod | xargs)
fi

DOMAIN="${DOMAIN:?Set DOMAIN in .env.prod (e.g. rutcampustrack.ru)}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:?Set CERTBOT_EMAIL in .env.prod}"
COMPOSE="docker compose -f docker-compose.prod.yml"

echo "=== RutCampusTrack SSL Bootstrap ==="
echo "Domain: $DOMAIN"
echo "Email:  $CERTBOT_EMAIL"
echo ""

# Step 1: Generate dhparam.pem if missing
if [ ! -f nginx/dhparam.pem ]; then
  echo ">>> Generating DH parameters (this takes ~1 minute)..."
  openssl dhparam -out nginx/dhparam.pem 2048
  echo ">>> dhparam.pem generated."
else
  echo ">>> dhparam.pem already exists, skipping."
fi

# Step 2: Ensure http-only config is active (no SSL references)
if [ -f nginx/conf.d/default.conf ] && [ ! -f nginx/conf.d/default.conf.disabled ]; then
  mv nginx/conf.d/default.conf nginx/conf.d/default.conf.disabled
  echo ">>> Moved default.conf -> default.conf.disabled"
fi
if [ -f nginx/conf.d/http-only.conf.bak ]; then
  mv nginx/conf.d/http-only.conf.bak nginx/conf.d/http-only.conf
  echo ">>> Restored http-only.conf from .bak"
fi

# Step 3: Start nginx with HTTP-only config
echo ">>> Starting nginx (HTTP-only mode)..."
$COMPOSE up -d nginx
sleep 5

# Step 4: Test with staging cert first (no rate limit risk)
echo ">>> Requesting STAGING certificate (test run)..."
$COMPOSE run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$CERTBOT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  --cert-name rutcampustrack \
  --staging \
  -d "$DOMAIN"

echo ""
echo ">>> Staging certificate issued successfully!"
echo ""
read -p "Proceed with PRODUCTION certificate? (y/N) " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborted. Staging cert remains. Re-run this script to continue."
  exit 0
fi

# Step 5: Issue real production certificate
echo ">>> Requesting PRODUCTION certificate..."
$COMPOSE run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$CERTBOT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  --cert-name rutcampustrack \
  --force-renewal \
  -d "$DOMAIN"

# Step 6: Swap to production nginx config (HTTPS enabled)
mv nginx/conf.d/default.conf.disabled nginx/conf.d/default.conf
mv nginx/conf.d/http-only.conf nginx/conf.d/http-only.conf.bak
echo ">>> Activated production nginx config (HTTPS enabled)."

# Step 7: Reload nginx to pick up SSL cert and new config
$COMPOSE exec nginx nginx -s reload
echo ""
echo "=== SSL Bootstrap Complete ==="
echo "Test: curl -I https://$DOMAIN"
echo ""
echo "Certbot sidecar will auto-renew. No need to run this script again."
