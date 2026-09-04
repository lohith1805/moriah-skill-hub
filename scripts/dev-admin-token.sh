#!/usr/bin/env bash
# DEV ONLY. Logs in as admin@moriah.test, completes the mandatory-2FA *setup*
# (enrols a fresh TOTP secret and prints it so you can save it in an
# authenticator app), then re-runs invoice generation + the confirmation
# email for a payment id.
#
#   bash scripts/dev-admin-token.sh [paymentId]   # default paymentId=6
#
# Needs: curl, python (both already on your PATH).

set -euo pipefail
BASE="http://localhost:8080/api/v1"
PAYMENT_ID="${1:-6}"
EMAIL="admin@moriah.test"
PASS="Password123!"

login=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
ct=$(printf '%s' "$login" | python -c "import sys,json;print(json.load(sys.stdin)['data']['challengeToken'])")
setup=$(printf '%s' "$login" | python -c "import sys,json;d=json.load(sys.stdin)['data'];print('1' if d.get('twoFactorSetupRequired') else '0')")

if [ "$setup" = "1" ]; then
  en=$(curl -s -X POST "$BASE/auth/2fa/enable" -H 'Content-Type: application/json' \
    -d "{\"challengeToken\":\"$ct\"}")
  secret=$(printf '%s' "$en" | python -c "import sys,json;print(json.load(sys.stdin)['data']['secret'])")
  uri=$(printf '%s' "$en" | python -c "import sys,json;print(json.load(sys.stdin)['data']['provisioningUri'])")
  echo "── 2FA secret enrolled for $EMAIL (save this in your authenticator) ──"
  echo "   secret: $secret"
  echo "   uri:    $uri"
  echo "──────────────────────────────────────────────────────────────────────"
  code=$(python - "$secret" <<'PY'
import base64,hashlib,hmac,struct,sys,time
s=sys.argv[1]
key=base64.b32decode(s.upper()+'='*((8-len(s)%8)%8))
c=int(time.time())//30
mac=hmac.new(key,struct.pack('>Q',c),hashlib.sha1).digest()
o=mac[-1]&0x0F
print("%06d"%((struct.unpack('>I',mac[o:o+4])[0]&0x7FFFFFFF)%1000000))
PY
)
else
  echo "admin already has 2FA enabled — enter a current 6-digit code:"
  read -r code
fi

ver=$(curl -s -X POST "$BASE/auth/2fa/verify" -H 'Content-Type: application/json' \
  -d "{\"challengeToken\":\"$ct\",\"totpCode\":\"$code\"}")
access=$(printf '%s' "$ver" | python -c "import sys,json;print(json.load(sys.stdin)['data']['tokens']['accessToken'])")
echo "got admin access token: ${access:0:24}..."

echo "── POST /dev/jobs/invoice-generation/run?paymentId=$PAYMENT_ID ──"
curl -s -X POST "$BASE/dev/jobs/invoice-generation/run?paymentId=$PAYMENT_ID" \
  -H "Authorization: Bearer $access" | python -m json.tool
