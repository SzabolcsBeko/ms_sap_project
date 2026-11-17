#!/usr/bin/env bash
#
# setup-keycloak-client.sh — register the confidential `user-service` client in the running Keycloak.
#
# Why this exists: Keycloak only imports a realm on first startup (the realm is then persisted in
# keycloak-mysql), so editing realm-export.json does not change an already-imported realm. This
# script adds the client additively via the Admin REST API — no realm delete, no container restart,
# no data loss. It is idempotent: re-running it is safe.
#
# Run it once after the stack is up and before registering users:
#   ./user-service/scripts/setup-keycloak-client.sh
#
# Override defaults with env vars if your setup differs:
#   KC_URL (http://localhost:8181)  KC_ADMIN (admin)  KC_ADMIN_PASSWORD (admin)
#   REALM (spring-microservices-security-realm)  CLIENT_SECRET (change-me-dev-secret)

set -euo pipefail

KC_URL="${KC_URL:-http://localhost:8181}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin}"
REALM="${REALM:-spring-microservices-security-realm}"
CLIENT_ID="user-service"
CLIENT_SECRET="${CLIENT_SECRET:-change-me-dev-secret}"
PASSWORD_POLICY="length(8) and digits(1) and upperCase(1) and lowerCase(1) and notUsername(undefined)"

say() { printf '==> %s\n' "$*"; }

say "Getting admin token from master realm"
TOKEN="$(curl -sf -d "client_id=admin-cli" -d "username=${KC_ADMIN}" -d "password=${KC_ADMIN_PASSWORD}" \
  -d "grant_type=password" "${KC_URL}/realms/master/protocol/openid-connect/token" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")"
[ -n "$TOKEN" ] || { echo "Could not obtain admin token (check KC_ADMIN / KC_ADMIN_PASSWORD)"; exit 1; }
AUTH=(-H "Authorization: Bearer ${TOKEN}")

say "Ensuring client '${CLIENT_ID}' exists"
EXISTING="$(curl -sf "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}/clients?clientId=${CLIENT_ID}")"
if [ "$(printf '%s' "$EXISTING" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')" = "0" ]; then
  curl -sf "${AUTH[@]}" -H "Content-Type: application/json" \
    -X POST "${KC_URL}/admin/realms/${REALM}/clients" \
    -d "{\"clientId\":\"${CLIENT_ID}\",\"enabled\":true,\"protocol\":\"openid-connect\",\"publicClient\":false,\"secret\":\"${CLIENT_SECRET}\",\"standardFlowEnabled\":false,\"implicitFlowEnabled\":false,\"directAccessGrantsEnabled\":false,\"serviceAccountsEnabled\":true}"
  say "  created"
else
  say "  already present (leaving it as is)"
fi

CID="$(curl -sf "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}/clients?clientId=${CLIENT_ID}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')"
SA_USER_ID="$(curl -sf "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}/clients/${CID}/service-account-user" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')"
RM_CID="$(curl -sf "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}/clients?clientId=realm-management" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')"

say "Assigning realm-management roles to the service account"
ROLES_JSON="$(curl -sf "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}/clients/${RM_CID}/roles" \
  | python3 -c '
import sys,json
want={"manage-users","view-users","query-users"}
roles=[{"id":r["id"],"name":r["name"]} for r in json.load(sys.stdin) if r["name"] in want]
print(json.dumps(roles))')"
curl -sf "${AUTH[@]}" -H "Content-Type: application/json" \
  -X POST "${KC_URL}/admin/realms/${REALM}/users/${SA_USER_ID}/role-mappings/clients/${RM_CID}" \
  -d "$ROLES_JSON"
say "  manage-users, view-users, query-users assigned"

say "Setting the realm password policy"
REALM_JSON="$(curl -sf "${AUTH[@]}" "${KC_URL}/admin/realms/${REALM}")"
printf '%s' "$REALM_JSON" | python3 -c "
import sys,json
d=json.load(sys.stdin)
d['passwordPolicy']='${PASSWORD_POLICY}'
print(json.dumps(d))" | curl -sf "${AUTH[@]}" -H "Content-Type: application/json" \
  -X PUT "${KC_URL}/admin/realms/${REALM}" -d @-
say "  password policy applied"

say "Done. Restart user-service only if it was already running before the client existed:"
say "  kill \$(lsof -ti tcp:8083); then ./launch.sh start"
