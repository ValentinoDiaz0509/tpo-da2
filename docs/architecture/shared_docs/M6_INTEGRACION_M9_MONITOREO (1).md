# Guía de integración: publicar eventos a Internación (M6) desde Monitoreo (M9)

**Health Grid · Módulo 6 (Internación y Gestión de Camas) — para el equipo de Módulo 9**

> Esta guía es para el equipo de **Monitoreo (M9)**: explica cómo publicar los eventos que le corresponden a M9 en el bus del Core (M10) para que M6 los procese. La cola y los eventos **ya están creados** en el Core — no hace falta dar de alta nada, solo publicar. Para el contrato genérico del bus (login, formato de mensajes) ver [`M10_integration.md`](integrations/M10_integration.md).

---

## 1. Qué le corresponde a M9

M9 tiene **dos** eventos hacia M6, ambos relacionados al código rojo:

| `event_type_id` | `name` | Cuándo se dispara |
|---|---|---|
| **16** | `internacion.alerta-emergencia.detectada` | Un sensor/regla de monitoreo detecta una emergencia en un paciente internado |
| **17** | `internacion.alerta-resuelta.notificada` | La emergencia se estabiliza |

Estos eventos reemplazan, en paralelo durante la transición, a los webhooks REST que M9 llama hoy: `POST /api/v1/webhooks/monitoreo/alerta-emergencia` y `POST /api/v1/webhooks/monitoreo/alerta-resuelta`. **Esos endpoints REST siguen activos** — no hace falta migrar de un día para el otro; M9 puede seguir usándolos mientras integra la publicación por bus, y ambos caminos conviven hasta que se coordine el retiro del REST.

M6 ya tiene la cola `internacion.requests` creada y ambos eventos dados de alta y bindeados a esa cola. **M9 no necesita crear ninguna cola ni evento propio para esto** — solo necesita publicar.

---

## 2. Prerrequisito: tu propia cuenta de servicio en el Core

M9 necesita su propia cuenta en el Core (M10) con permiso `events:log:publish`. Se loguea como cualquier otro cliente:

```http
POST /auth/login
Content-Type: application/json

{ "email": "<cuenta-servicio-m9@...>", "password": "<...>" }
```

Respuesta `200`:

```json
{ "token": "eyJhbGciOiJSUzI1NiIs...", "user": { "id": 15, "email": "...", ... } }
```

Guardar el `token` y usarlo como `Authorization: Bearer <token>` en la publicación (§3). El token del Core dura 24 h — renovar el login cuando expire.

---

## 3. Cómo publicar cada evento

### 3.1 Alerta de emergencia detectada (`event_type_id: 16`)

```http
POST /events/log
Authorization: Bearer <token de tu cuenta de servicio>
Content-Type: application/json

{
  "event_type_id": 16,
  "publisher_module": "monitoreo",
  "payload": "{\"paciente_id\":123,\"observaciones\":\"Frecuencia cardíaca sostenida > 120 por 2 minutos\",\"timestamp\":\"2026-07-10T14:00:00Z\"}"
}
```

### 3.2 Alerta resuelta (`event_type_id: 17`)

```http
POST /events/log
Authorization: Bearer <token de tu cuenta de servicio>
Content-Type: application/json

{
  "event_type_id": 17,
  "publisher_module": "monitoreo",
  "payload": "{\"paciente_id\":123,\"motivo_resolucion\":\"Paciente estabilizado\",\"timestamp\":\"2026-07-10T14:05:00Z\"}"
}
```

Notar: `payload` va **como string JSON** (no como objeto anidado) — es el formato que exige el Core para todo `POST /events/log`, no algo específico de M6.

Respuesta `201` en ambos casos:

```json
{
  "id": 91,
  "event_type_id": 16,
  "publisher_module": "monitoreo",
  "payload": "...",
  "status": "pending",
  ...
}
```

Con eso, el Core enruta el mensaje a `internacion.requests` (ya bindeado) y M6 lo procesa de forma asíncrona. No hay respuesta síncrona sobre el resultado del procesamiento — es fire-and-forget, igual que hoy son los webhooks REST.

---

## 4. Contrato de payload

### 4.1 `internacion.alerta-emergencia.detectada` (id 16)

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `paciente_id` | integer | **sí** | Id del paciente en el Core. M6 lo usa para localizar la cama actual del paciente. |
| `observaciones` | string | no | Texto libre, contexto de la alerta (opcional). |
| `timestamp` | string (ISO 8601, UTC) | sí | Momento de detección de la emergencia. |

```json
{
  "paciente_id": 123,
  "observaciones": "Frecuencia cardíaca sostenida > 120 por 2 minutos",
  "timestamp": "2026-07-10T14:00:00Z"
}
```

### 4.2 `internacion.alerta-resuelta.notificada` (id 17)

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `paciente_id` | integer | **sí** | Mismo paciente de la alerta de emergencia. |
| `motivo_resolucion` | string | no | Texto libre (opcional). |
| `timestamp` | string (ISO 8601, UTC) | sí | Momento de resolución. |

```json
{
  "paciente_id": 123,
  "motivo_resolucion": "Paciente estabilizado",
  "timestamp": "2026-07-10T14:05:00Z"
}
```

---

## 5. Qué hace M6 al recibir cada evento

**Alerta de emergencia (16):**
1. Busca la cama `OCUPADA` del paciente (`paciente_id`). Si no tiene cama asignada, el mensaje se descarta (equivalente al `404` que hoy devuelve el REST) — no hay retry automático de M6 hacia M9.
2. La marca en estado `CODIGO_ROJO` y registra el evento en la historia clínica del paciente (M1).

**Alerta resuelta (17):**
1. Busca la cama en `CODIGO_ROJO` del paciente. Si no encuentra ninguna (por ejemplo, la alerta ya se resolvió antes), el mensaje se descarta — no hay error visible del lado de M9.
2. La cama vuelve a `OCUPADA`.

**Orden de los eventos:** publicar primero `alerta-emergencia.detectada` y, cuando corresponda, `alerta-resuelta.notificada` para el mismo `paciente_id`. Si `alerta-resuelta` llega sin que exista una cama en `CODIGO_ROJO` (por ejemplo, por un reordenamiento de mensajes en el bus), M6 no encuentra qué resolver y descarta el mensaje — no hay reconciliación automática. Publicar en orden evita este caso.

---

## 6. Errores comunes

| Síntoma | Causa probable |
|---|---|
| `401` al publicar | Token vencido o cuenta sin permiso `events:log:publish` |
| `400` al publicar | Falta `event_type_id` o `publisher_module` en el body de `POST /events/log` |
| El evento se publica (`201`) pero M6 nunca actúa | Para `alerta-emergencia`: verificar que el paciente tenga una cama `OCUPADA` real en M6. Para `alerta-resuelta`: verificar que exista una cama en `CODIGO_ROJO` para ese paciente (puede que la alerta de emergencia no haya llegado, o haya llegado después) |
| Necesito saber si M6 procesó bien el evento | Hoy no hay canal de respuesta — es fire-and-forget. Si tu flujo necesita confirmación, seguir usando el REST directo mientras tanto |

---

## 7. Checklist de integración

- [ ] Cuenta de servicio de M9 en el Core, con permiso `events:log:publish`.
- [ ] Login (`POST /auth/login`) y manejo de renovación del token (24 h).
- [ ] Publicar `POST /events/log` con `event_type_id: 16` al detectar una emergencia.
- [ ] Publicar `POST /events/log` con `event_type_id: 17` al resolverse la emergencia, para el mismo `paciente_id`.
- [ ] Publicar los eventos en orden (emergencia antes que resuelta) para el mismo paciente.
- [ ] Coordinar con el equipo de M6 cuándo dejar de llamar también a los REST directos (`/webhooks/monitoreo/alerta-emergencia`, `/webhooks/monitoreo/alerta-resuelta`) — mientras tanto, no hay problema en llamar a ambos.

---

## 8. Referencias

- [`M6_EVENTOS_RABBITMQ.md`](M6_EVENTOS_RABBITMQ.md) — infraestructura completa de M6 en el bus (los 5 eventos entrantes, no solo los de M9).
- [`integrations/M10_integration.md`](integrations/M10_integration.md) — tutorial genérico del bus de eventos del Core.
- [`M6_DISEÑO.md`](M6_DISEÑO.md) §5.7 — flujo de negocio de código rojo; §7.5 — contexto de la migración de webhooks REST a eventos.
