#!/usr/bin/env bash
set -euo pipefail

# ================================================
#  Configuración
# ================================================
NODE1="http://localhost:8080/alfresco"
NODE2="http://localhost:8180/alfresco"
USER="admin"
PASS="admin"

AUTH_API_PATH="/api/-default-/public/authentication/versions/1"
NODES_API_PATH="/api/-default-/public/alfresco/versions/1"

# ================================================
#  Funciones auxiliares
# ================================================

parse_ticket_from_body() {
  local body="$1"
  local ticket=""

  if command -v jq >/dev/null 2>&1; then
    ticket=$(echo "$body" | jq -r '.entry.id // empty')
  else
    ticket=$(echo "$body" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)
  fi

  echo "$ticket"
}

get_ticket() {
  local base_url="$1"
  local resp http_code body ticket

  resp=$(curl -s -w "\n%{http_code}" \
    -H "Content-Type: application/json" \
    -d '{"userId":"'"$USER"'","password":"'"$PASS"'"}' \
    "$base_url$AUTH_API_PATH/tickets")

  http_code=$(echo "$resp" | tail -n1)
  body=$(echo "$resp" | sed '$d')

  if [[ "$http_code" != "200" && "$http_code" != "201" ]]; then
    echo "ERROR: login en $base_url (HTTP $http_code)" >&2
    echo "Respuesta: $body" >&2
    return 1
  fi

  ticket=$(parse_ticket_from_body "$body")

  if [[ -z "$ticket" ]]; then
    echo "ERROR: no se pudo extraer el ticket de la respuesta de $base_url" >&2
    echo "Respuesta: $body" >&2
    return 1
  fi

  echo "$ticket"
}

validate_ticket_on_other_node() {
  local base_url="$1"
  local ticket="$2"
  local http_code

  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    "$base_url$NODES_API_PATH/nodes/-root-?alf_ticket=$ticket")

  if [[ "$http_code" == "200" ]]; then
    return 0
  else
    echo "Validación FALLIDA en $base_url (HTTP $http_code)" >&2
    return 1
  fi
}

print_header() {
  cat <<EOF
============================================================
  Comprobación de clúster Alfresco por tickets de sesión
============================================================
Nodo 1: $NODE1
Nodo 2: $NODE2
Usuario: $USER

EOF
}

print_header

echo "1) Ticket en NODE1 y validación en NODE2"
echo "----------------------------------------"

if ticket1=$(get_ticket "$NODE1"); then
  echo "Ticket obtenido en NODE1: $ticket1"
  if validate_ticket_on_other_node "$NODE2" "$ticket1"; then
    echo "OK: ticket de NODE1 es válido en NODE2"
  else
    echo "FALLO: ticket de NODE1 NO es válido en NODE2"
  fi
else
  echo "FALLO en login NODE1"
fi

echo
echo "2) Ticket en NODE2 y validación en NODE1"
echo "----------------------------------------"

if ticket2=$(get_ticket "$NODE2"); then
  echo "Ticket obtenido en NODE2: $ticket2"
  if validate_ticket_on_other_node "$NODE1" "$ticket2"; then
    echo "OK: ticket de NODE2 es válido en NODE1"
  else
    echo "FALLO: ticket de NODE2 NO es válido en NODE1"
  fi
else
  echo "FALLO en login NODE2"
fi

echo
echo "Comprobación completada."