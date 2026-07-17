#!/usr/bin/env bash
#
# provision-core-rabbit.sh
# ------------------------------------------------------------------------------
# Provisiona en el Core (M10) el flujo de ENTRADA de M9 (admisión M6 -> M9):
#   1. Login al Core            (POST /auth/login)
#   2. Crear cola de requests   (POST /rabbit/queues)  -> monitoring.requests (+ .dlq)
#   3. Crear DOS eventos        (POST /events/types)   -> ALTA_EVENT_ID / BAJA_EVENT_ID
#   4. Bindear ambos <-> cola   (POST /rabbit/bindings)
#
# M6 pidió un evento para dar de ALTA y otro para dar de BAJA: el tipo de evento
# en sí distingue la operación (el payload solo lleva paciente_id + timestamp).
# Ambos eventos se bindean a la misma cola monitoring.requests.
#
# Al final imprime ALTA_EVENT_ID / BAJA_EVENT_ID y el contrato para pasarle a M6.
#
# La SALIDA (alertas M9->M6, ids 16/17) ya existe en el Core: no se provisiona acá.
# La telemetría es interna: no toca RabbitMQ.
#
# Requisitos: bash, curl, python3 (NO requiere jq).
#
# Uso:
#   CORE_EMAIL=... CORE_PASSWORD=... ./provision-core-rabbit.sh
# o de forma interactiva (te pide lo que falte):
#   ./provision-core-rabbit.sh
# ------------------------------------------------------------------------------
set -euo pipefail

# ── Config (overrideable por env) ─────────────────────────────────────────────
CORE_URL="${CORE_URL:-https://api.healthcare.cantero.ar}"
QUEUE_NAME="${QUEUE_NAME:-monitoring}"                              # -> monitoring.requests
ALTA_EVENT_NAME="${ALTA_EVENT_NAME:-monitoring.alta.requested}"
BAJA_EVENT_NAME="${BAJA_EVENT_NAME:-monitoring.baja.requested}"
ALTA_EVENT_DESC="${ALTA_EVENT_DESC:-M6 da de alta el monitoreo de un paciente}"
BAJA_EVENT_DESC="${BAJA_EVENT_DESC:-M6 da de baja el monitoreo de un paciente}"
SOURCE_MODULE="${SOURCE_MODULE:-monitoring}"

# ── Helpers ───────────────────────────────────────────────────────────────────
c_red=$'\e[31m'; c_grn=$'\e[32m'; c_ylw=$'\e[33m'; c_bld=$'\e[1m'; c_rst=$'\e[0m'
say()  { printf '%s\n' "$*"; }
ok()   { printf '%s✓%s %s\n' "$c_grn" "$c_rst" "$*"; }
warn() { printf '%s!%s %s\n' "$c_ylw" "$c_rst" "$*"; }
die()  { printf '%s✗ %s%s\n' "$c_red" "$*" "$c_rst" >&2; exit 1; }

command -v curl    >/dev/null || die "curl no está instalado"
command -v python3 >/dev/null || die "python3 no está instalado"

# Extrae una clave de un JSON por stdin (devuelve vacío si no existe). Sin jq.
json_get() { python3 -c "import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    sys.exit(0)
v=d.get('$1') if isinstance(d, dict) else None
print('' if v is None else v)"; }

# POST helper: imprime status + body y deja el body en la global RESP_BODY.
# Uso: http_post <path> <json-body> [auth]
RESP_CODE=""; RESP_BODY=""
http_post() {
  local path="$1" body="$2" auth="${3:-}"
  local tmp; tmp="$(mktemp)"
  local -a hdr=(-H 'Content-Type: application/json')
  [[ -n "$auth" ]] && hdr+=(-H "Authorization: Bearer $auth")
  RESP_CODE="$(curl -sS -o "$tmp" -w '%{http_code}' -X POST "${CORE_URL}${path}" "${hdr[@]}" -d "$body")"
  RESP_BODY="$(cat "$tmp")"; rm -f "$tmp"
}

# ── Credenciales ──────────────────────────────────────────────────────────────
CORE_EMAIL="${CORE_EMAIL:-}"
CORE_PASSWORD="${CORE_PASSWORD:-}"
if [[ -z "$CORE_EMAIL" ]]; then read -rp "Email de tu cuenta de servicio en el Core: " CORE_EMAIL; fi
if [[ -z "$CORE_PASSWORD" ]]; then read -rsp "Password del Core: " CORE_PASSWORD; echo; fi
[[ -n "$CORE_EMAIL" && -n "$CORE_PASSWORD" ]] || die "Faltan credenciales del Core"

say ""
say "${c_bld}Core:${c_rst} $CORE_URL"
say "${c_bld}Cola:${c_rst} ${QUEUE_NAME}.requests   ${c_bld}Eventos:${c_rst} ${ALTA_EVENT_NAME} · ${BAJA_EVENT_NAME}"
say ""

# ── Paso 1: login ─────────────────────────────────────────────────────────────
say "1) Login en el Core..."
http_post /auth/login "$(python3 -c "import json,sys;print(json.dumps({'email':sys.argv[1],'password':sys.argv[2]}))" "$CORE_EMAIL" "$CORE_PASSWORD")"
[[ "$RESP_CODE" == 2* ]] || die "Login falló (HTTP $RESP_CODE): $RESP_BODY"
TOKEN="$(printf '%s' "$RESP_BODY" | json_get token)"
[[ -n "$TOKEN" ]] || die "No se pudo extraer el token del login: $RESP_BODY"
ok "Token obtenido"

# ── Paso 2: crear cola de requests ────────────────────────────────────────────
say "2) Creando cola ${QUEUE_NAME}.requests..."
http_post /rabbit/queues "{\"queue_name\":\"${QUEUE_NAME}\",\"queue_type\":\"requests\"}" "$TOKEN"
if [[ "$RESP_CODE" == 2* ]]; then
  ok "Cola creada: $(printf '%s' "$RESP_BODY" | json_get queue_name) (dlq: $(printf '%s' "$RESP_BODY" | json_get dlq_name))"
elif [[ "$RESP_CODE" == 409 || "$RESP_BODY" == *xist* ]]; then
  warn "La cola ya existía (HTTP $RESP_CODE) — sigo."
else
  die "No se pudo crear la cola (HTTP $RESP_CODE): $RESP_BODY"
fi

# ── Pasos 3-4: crear cada evento y bindearlo a la cola ────────────────────────
# provision_event <name> <desc>  -> deja el id resultante en la global EVENT_ID_OUT
EVENT_ID_OUT=""
provision_event() {
  local name="$1" desc="$2"
  EVENT_ID_OUT=""
  say "   • Evento ${name}..."
  http_post /events/types "$(python3 -c "import json,sys;print(json.dumps({'name':sys.argv[1],'description':sys.argv[2],'source_module':sys.argv[3]}))" "$name" "$desc" "$SOURCE_MODULE")" "$TOKEN"
  if [[ "$RESP_CODE" == 2* ]]; then
    EVENT_ID_OUT="$(printf '%s' "$RESP_BODY" | json_get id)"
    ok "Evento creado id=${EVENT_ID_OUT}"
  elif [[ "$RESP_CODE" == 409 || "$RESP_BODY" == *xist* ]]; then
    warn "El evento ya existía (HTTP $RESP_CODE)."
    EVENT_ID_OUT="$(printf '%s' "$RESP_BODY" | json_get id)"
    [[ -z "$EVENT_ID_OUT" ]] && warn "No vino el id; buscalo en ${CORE_URL}/swagger o pedíselo a Core."
  else
    die "No se pudo crear el evento ${name} (HTTP $RESP_CODE): $RESP_BODY"
  fi

  if [[ -n "$EVENT_ID_OUT" ]]; then
    http_post /rabbit/bindings "{\"event_id\":${EVENT_ID_OUT},\"queue_name\":\"${QUEUE_NAME}.requests\"}" "$TOKEN"
    if [[ "$RESP_CODE" == 2* ]]; then
      ok "Binding ${EVENT_ID_OUT} <-> ${QUEUE_NAME}.requests creado"
    elif [[ "$RESP_CODE" == 409 || "$RESP_BODY" == *xist* ]]; then
      warn "El binding ya existía (HTTP $RESP_CODE)."
    else
      die "No se pudo crear el binding (HTTP $RESP_CODE): $RESP_BODY"
    fi
  else
    warn "Salteo el binding de ${name}: no tengo el id. Bindealo a mano cuando lo tengas."
  fi
}

say "3-4) Creando eventos (alta / baja) y sus bindings..."
provision_event "$ALTA_EVENT_NAME" "$ALTA_EVENT_DESC"; ALTA_EVENT_ID="$EVENT_ID_OUT"
provision_event "$BAJA_EVENT_NAME" "$BAJA_EVENT_DESC"; BAJA_EVENT_ID="$EVENT_ID_OUT"

# ── Resumen para M6 ───────────────────────────────────────────────────────────
say ""
say "${c_bld}================ LISTO — pasale esto a M6 ================${c_rst}"
say "  Cola de entrada (M9 escucha):  ${QUEUE_NAME}.requests"
say ""
say "  ${c_bld}ALTA${c_rst}  event_type_id = ${ALTA_EVENT_ID:-<pendiente>}   (${ALTA_EVENT_NAME})"
say "  ${c_bld}BAJA${c_rst}  event_type_id = ${BAJA_EVENT_ID:-<pendiente>}   (${BAJA_EVENT_NAME})"
say ""
say "  El TIPO de evento distingue la operación. M6 publica (POST ${CORE_URL}/events/log):"
say ""
say "  ALTA:"
say '    {'
say "      \"event_type_id\": ${ALTA_EVENT_ID:-<ALTA_EVENT_ID>},"
say '      "publisher_module": "internacion",'
say '      "payload": "{\"paciente_id\":123,\"cama_id\":45,\"timestamp\":\"2026-07-17T14:00:00Z\"}"'
say '    }'
say ""
say "  BAJA:"
say '    {'
say "      \"event_type_id\": ${BAJA_EVENT_ID:-<BAJA_EVENT_ID>},"
say '      "publisher_module": "internacion",'
say '      "payload": "{\"paciente_id\":123,\"cama_id\":45,\"timestamp\":\"2026-07-17T14:05:00Z\"}"'
say '    }'
say ""
say "  Contrato del payload interno:"
say '    paciente_id : integer   (id del paciente en el Core) — obligatorio'
say '    cama_id     : integer   (id de la cama en M6)'
say '    timestamp   : string ISO-8601 UTC'
say "${c_bld}=========================================================${c_rst}"
say ""
say "Ahora arrancá la app con las credenciales AMQP:"
say "  export RABBITMQ_HOST=queue.healthgrid.cantero.ar RABBITMQ_USER=... RABBITMQ_PASSWORD=..."
say "  export MODULE10_CORE_EMAIL=${CORE_EMAIL} MODULE10_CORE_PASSWORD=..."
say "  mvn spring-boot:run"
