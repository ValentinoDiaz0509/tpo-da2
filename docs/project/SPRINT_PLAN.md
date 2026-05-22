# Sprint Plan — Módulo 9 (Monitoreo de Pacientes)

**Cátedra:** Desarrollo de Aplicaciones II — TPO Health Grid
**Hoy:** 2026-05-17 (domingo)
**Deadline entrega final:** 2026-06-26 (viernes)
**Ventana total disponible:** 40 días (~5.7 semanas)
**Modelo de trabajo:** 4 sprints de 1 semana (3 de desarrollo+deploy + 1 de testing/docs) + 12 días de buffer hasta la entrega.

---

## 1. Equipo

| Persona | Área |
|---|---|
| Lucas Pascual | Backend |
| Valentino Diaz | Backend + Frontend |
| Felipe Parrondo | PM + Backend + Cloud |
| Nicolás Chávez | PM + Cloud |
| Gianluca Calabria | Frontend |
| Facundo Gainski | Frontend |

> Las tareas no están asignadas: el reparto se hace en el planning de cada sprint según disponibilidad y carga.

---

## 2. Contexto

Somos el **Módulo 9 – Monitoreo de Pacientes** del sistema Health Grid. Responsabilidades según el TPO:

- **Ingesta de telemetría** desde sensores vía colas de mensajes.
- **Motor de reglas** que evalúa lecturas en tiempo real (ej. HR > 120 sostenido 2 min).
- **Panel de monitoreo** para enfermería con estado de pacientes.
- **Alertas de emergencia** → evento de alta prioridad al **Módulo 6 (Internación)**.

### Estado actual del código

**Backend (`backend/`, Spring Boot 3.3 / Java 17):**
- Entidades `Patient`, `TelemetryReading`, `Rule`, `Alert` con repos JPA.
- Servicios: `RuleEngineService` (ventana 10 min, violaciones sostenidas), `HealthRuleEvaluationService`, `AlertService`, `EventPublisherService` (webhook a Módulo 6), `TelemetrySimulatorService`.
- Ingesta SQS (Spring Cloud Stream + LocalStack): `TelemetryConsumer`, `PatientEventConsumer`.
- WebSocket STOMP en `/api/v1/ws`, topic `/topic/monitoring/{patientId}`.
- Auth JWT (`AuthenticationController` **simulando Módulo 10 – Core** — marcado para reemplazar).
- Webhook receptor `InternacionWebhookController` para eventos de admisión.
- Dashboard aggregator `GET /patients/monitoring`.

**Frontend (`frontend/`, React 19 + Vite, JS):**
- Vistas: `Login`, `Monitoring` (lista de pacientes), `PatientDetail` (gráficos + WS).
- Componentes: `Header`, `Sidebar`, `RuleEngineModal`.
- `AuthContext` + `ProtectedRoute`, `services/api.js`, `services/websocket.js`.

### Brechas vs. entrega final del TPO

| Requisito | Estado | Falta |
|---|---|---|
| Módulo funcional | 🟡 | Hardening, simulador apagable en escenarios reales |
| Integrado al resto | 🔴 | JWT real con Módulo 10, webhook real con Módulo 6, contrato con Módulo 1 (HCE) si aplica |
| README instalación | 🟡 | Consolidar versión final |
| Swagger/Postman | 🟡 | Colección Postman estable, doc de eventos |
| Deploy | 🔴 | AWS de cátedra disponible pero sin infra |
| Tests | 🔴 | Base mínima; faltan unit, integración y E2E |

---

## 3. Cronograma global

```
                          May                              Jun
 Sem |  Lun       Vie     |  Lun       Vie     |  Lun       Vie     |  Lun       Vie     |  Lun                 Vie
-----+--------------------+--------------------+--------------------+--------------------+----------------------------
 S1  | 05-18 → 05-24      |                    |                    |                    |
 S2  |                    | 05-25 → 05-31      |                    |                    |
 S3  |                    |                    | 06-01 → 06-07      |                    |
 S4  |                    |                    |                    | 06-08 → 06-14      |
 Buf |                    |                    |                    |                    | 06-15 → 06-26 (deadline)
```

---

## 4. Sprint 1 — Integración de contratos y hardening (2026-05-18 → 2026-05-24)

**Meta:** firmar contratos con módulos peers (6 y 10), preparar el motor para uso real, limpiar deuda técnica del MVP heredado.

### Épica: Integración con Módulo 10 (Core/Auth)
- **HU-101** — Como sistema, quiero validar tokens JWT emitidos por el Módulo 10 real, para autenticar usuarios sin emitir tokens propios.
  - Reunión con grupo de Core para definir claims, algoritmo de firma, endpoint de validación.
  - Documentar contrato en `docs/INTEGRATION_CORE.md`.
  - Agregar flag `auth.core.mode=mock|remote` para alternar entre el emisor actual y el real.
  - Mantener el mock funcionando hasta que el real esté disponible.

### Épica: Integración con Módulo 6 (Internación)
- **HU-102** — Como sistema, quiero enviar eventos de alerta crítica al Módulo 6 en el formato y canal acordado, para disparar el flujo de internación.
  - Reunión con grupo de Internación: definir URL/cola SQS, schema del `AdmissionEventDTO`.
  - Validar `EventPublisherService` contra el contrato firmado.
  - Documentar en `docs/INTEGRATION_INTERNACION.md`.
- **HU-103** — Como sistema, quiero recibir eventos de admisión desde el Módulo 6 con idempotencia, para no duplicar pacientes ni telemetría.
  - Revisar `InternacionWebhookController`: validación de payload, manejo de duplicados (idempotency key), respuesta consistente.

### Épica: Hardening del backend
- **HU-104** — Como operador, quiero poder apagar el simulador de telemetría por configuración, para no contaminar el flujo cuando hay telemetría real.
  - Verificar flag `simulator.enabled` en `application.yml`.
  - Crear perfil `integration` con simulador deshabilitado.
  - Documentar en `CLAUDE.md` y README.
- **HU-105** — Como equipo, quiero un baseline de schema versionado, para evitar que `ddl-auto: update` rompa producción.
  - Exportar DDL actual a `src/main/resources/db/migration/V1__baseline.sql`.
  - Evaluar adopción de Flyway o Liquibase.
  - Cambiar `ddl-auto` a `validate` en perfil prod.

### Épica: UX inicial
- **HU-106** — Como personal de enfermería, quiero filtrar pacientes por sala y severidad en el panel, para encontrar rápido los casos críticos.
  - Filtros en vista `Monitoring`.
  - Badges con colores consistentes según `AlertSeverity`.
- **HU-107** — Como personal de enfermería, quiero reconocer (ACK) una alerta desde la UI, para indicar que ya estoy atendiéndola.
  - Botón ACK en lista de alertas.
  - Llamada a `PATCH /alerts/{id}/acknowledge`.
  - Refrescar estado en lista y en WebSocket.
- **HU-108** — Como desarrollador, quiero que el frontend lea la URL del backend de variables de entorno, para poder deployar a distintos ambientes.
  - Reemplazar `http://localhost:8080/api/v1` hardcoded por `VITE_API_BASE_URL`.

### Épica: Plataforma cloud
- **HU-109** — Como equipo, quiero confirmar acceso y permisos en la cuenta AWS de cátedra, para no descubrir bloqueos en S3.
  - Verificar permisos para SQS, RDS, EC2/ECS, IAM en `docs/awsUsers.md`.
- **HU-110** — Como equipo, quiero un plan de deploy con arquitectura definida, para no improvisar en S3.
  - Decidir entre ECS Fargate, EC2 con Docker Compose, o Elastic Beanstalk.
  - Diagrama + costos estimados en `docs/DEPLOYMENT_PLAN.md`.
- **HU-111** — Como equipo, quiero CI básica que valide cada PR, para detectar regresiones temprano.
  - GitHub Actions: `mvn package` backend, `npm run build` + `npm run lint` frontend.

**Definition of Done S1:**
- Contratos con Módulos 6 y 10 documentados.
- Simulador apagable, baseline DDL commiteado, ACK de alertas funcional en UI.
- Pipeline CI corre en cada PR.
- Demo interna viernes 23/05: alerta generada → llega al Módulo 6 (real o stub).

---

## 5. Sprint 2 — Integraciones reales + UX completa (2026-05-25 → 2026-05-31)

**Meta:** flujos end-to-end con módulos peers funcionando (aunque algunos sean stubs acordados), y UI que refleja todo el dominio.

### Épica: Auth en producción
- **HU-201** — Como sistema, quiero usar tokens emitidos por Módulo 10 sin emisor propio en producción, para cumplir el requisito de integración.
  - Si Core tiene endpoint: validar firma (clave pública o secreto compartido).
  - Si no: stub conjunto acordado con la cátedra.
  - Quitar TODOs de `AuthenticationController`.

### Épica: Eventos críticos
- **HU-202** — Como sistema, quiero publicar alertas críticas a una cola SQS resiliente con reintentos y DLQ, para no perder eventos ante caídas del Módulo 6.
  - Migrar de webhook HTTP a SQS si así se acordó.
  - Configurar DLQ y retry policy.

### Épica: Consulta a HCE (Módulo 1) — opcional según contrato
- **HU-203** — Como sistema, quiero consultar contraindicaciones del paciente desde el Módulo 1 antes de enriquecer una alerta, para dar contexto clínico al equipo médico.
  - Cliente HTTP con timeout y cache corta.
  - Sólo si HU-203 se acuerda con grupo de HCE; si no, queda para backlog.

### Épica: Configuración de reglas
- **HU-204** — Como administrador médico, quiero crear, editar, activar y desactivar reglas desde la UI, para ajustar el umbral sin tocar la base.
  - Endpoints CRUD validados con Swagger + ejemplos.
  - `RuleEngineModal` con formularios, validación y feedback.

### Épica: Detalle de paciente
- **HU-205** — Como médico, quiero ver los gráficos históricos del paciente (2h, 24h) con las líneas de threshold de las reglas activas, para entender la evolución.
  - Recharts con series temporales.
  - Tabla de alertas recientes del paciente.
- **HU-206** — Como personal de enfermería, quiero recibir una alerta visible y audible cuando un paciente entre en estado crítico, para reaccionar inmediatamente.
  - Banner global de alerta crítica con sonido, descartable.
- **HU-207** — Como usuario, quiero loguearme con mis credenciales reales del Módulo 10, para acceder al panel.
  - Vista `Login` apuntando al endpoint final de Core.

### Épica: Infra AWS
- **HU-208** — Como equipo, quiero infraestructura base levantada en AWS, para poder deployar el backend en S3.
  - RDS PostgreSQL (`db.t3.micro`).
  - Dos colas SQS (telemetry + admission).
  - Security groups documentados.
- **HU-209** — Como equipo, quiero el backend dockerizado y publicado en un registry, para poder deployarlo desde la pipeline.
  - Dockerfile multistage para backend.
  - Imagen publicada a ECR (o probada localmente).

**Definition of Done S2:**
- E2E manual: login → ver paciente → recibir telemetría → alerta visible → evento al Módulo 6.
- CRUD reglas funcional en UI.
- Backend corriendo contra RDS de AWS al menos desde local.
- Demo interna viernes 30/05.

---

## 6. Sprint 3 — Deploy + cierre funcional (2026-06-01 → 2026-06-07)

**Meta:** todo corriendo en AWS, code freeze el viernes 06/06, sólo bugs y polish después.

### Épica: Deploy backend
- **HU-301** — Como equipo, quiero el backend desplegado en AWS detrás de HTTPS, para poder consumirlo desde el frontend público.
  - ECS Fargate / EC2 (según decisión S1).
  - ALB con certificado ACM.
  - Health/readiness endpoints validados.
- **HU-302** — Como equipo, quiero que los secretos (JWT, credenciales DB) vivan en un servicio gestionado, para no exponerlos en el repo.
  - SSM Parameter Store o Secrets Manager.
  - Variables de entorno por ambiente.

### Épica: Deploy frontend
- **HU-303** — Como usuario, quiero una URL pública para el panel, para acceder desde cualquier dispositivo.
  - S3 + CloudFront (o equivalente).
  - Build de producción con env vars correctas, source maps off.
- **HU-304** — Como enfermería con tablet, quiero que la vista `Monitoring` funcione en pantallas medianas, para usarla en el piso.
  - Layout responsive mínimo en `Monitoring`.

### Épica: Observabilidad y operación
- **HU-305** — Como operador, quiero logs estructurados en producción, para diagnosticar incidentes.
  - JSON logs en backend.
  - Variables (`MODULE6_WEBHOOK_URL`, `JWT_*`) desde env.
- **HU-306** — Como equipo, quiero un smoke test posdeploy desde un dispositivo externo, para confirmar que el ambiente está vivo.
  - Checklist manual o script.

### Code freeze
- Viernes 06/06 EOD: no se mergean features nuevas. Sólo bugs y documentación.

**Definition of Done S3:**
- URL pública del frontend conectada al backend en AWS.
- Telemetría simulada genera alertas visibles en producción.
- README actualizado con URL, credenciales de demo y pasos de deploy.
- Demo interna viernes 06/06: flujo completo corriendo en AWS.

---

## 7. Sprint 4 — Testing + Documentación (2026-06-08 → 2026-06-14)

**Meta:** entregar el módulo con tests significativos, documentación final y demo grabada.

### Épica: Tests backend
- **HU-401** — Como equipo, quiero tests unitarios del motor de reglas, para evitar regresiones en la lógica crítica.
  - `RuleEngineService.detectSustainedViolation`.
  - `HealthRuleEvaluationService` con los 6 operadores.
  - `PatientStatusCalculator`.
- **HU-402** — Como equipo, quiero tests de integración del flujo SQS → persistencia → alerta, para validar el camino crítico.
  - `spring-cloud-stream-test-binder` ya está en el stack.
  - Mensajes mock cubren caso normal, caso de violación sostenida, caso sin patient match.
- **HU-403** — Como equipo, quiero tests de los controllers clave, para asegurar contratos REST estables.
  - `MonitoringController` (snapshot).
  - `AlertController` (ACK).
  - Cobertura ≥60% en `service/` y `consumer/`.

### Épica: Tests frontend y E2E
- **HU-404** — Como equipo, quiero `npm run lint` sin warnings, para mantener calidad mínima del frontend.
- **HU-405** — Como equipo, quiero un guion E2E manual reproducible, para usar en defensa y regresiones.
  - Documentado paso a paso con datos de prueba.

### Épica: Documentación final
- **HU-406** — Como evaluador, quiero un README raíz claro con cómo levantar y deployar, para entender el módulo sin leer 20 archivos.
  - Requisitos, Docker Compose local, pasos de deploy, contactos.
- **HU-407** — Como evaluador, quiero diagramas de arquitectura actualizados con integraciones, para entender cómo encaja en Health Grid.
  - Actualizar `docs/ARCHITECTURE.md` con módulos 6 y 10.
- **HU-408** — Como evaluador, quiero una colección Postman lista, para probar endpoints sin configurar nada.
  - Export en `docs/postman/healthgrid-modulo9.json`.
  - JWT pre-request configurado.
- **HU-409** — Como evaluador, quiero Swagger completo con descripciones y ejemplos, para entender contratos REST.
- **HU-410** — Como evaluador, quiero un catálogo de eventos con schemas, para entender la integración asincrónica.
  - `docs/EVENTS_CATALOG.md`: topics SQS y WS con payloads.
- **HU-411** — Como personal de enfermería, quiero un manual breve con screenshots, para usar el panel sin entrenamiento.
- **HU-412** — Como equipo, quiero un guion de defensa con justificación tech y arquitectura, para presentar en clase.

**Definition of Done S4:**
- `mvn test` verde con cobertura mínima del 60% en `service/` y `consumer/`.
- README + ARCHITECTURE + EVENTS_CATALOG + Postman commiteados.
- Demo grabada (5–7 min) lista para presentar.

---

## 8. Buffer (2026-06-15 → 2026-06-26)

12 días para:
- Reuniones de integración cross-módulo final (especialmente con Módulos 6 y 10).
- Fixes derivados de pruebas cruzadas con otros grupos.
- Ensayo de defensa con todo el equipo.
- Margen para imprevistos (ajustes pedidos por el docente, problemas de AWS, ausencias).

**Hito sugerido:** dry-run completo del sistema integrado el viernes 20/06.

---

## 9. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Grupo de Módulo 10 no entrega JWT a tiempo | Alto — bloquea auth real | Mantener flag `auth.core.mode=mock` con tokens propios; entregable independiente |
| Grupo de Módulo 6 cambia contrato de webhook | Medio | Definir contrato en S1; usar SQS si HTTP cambia |
| AWS de cátedra con permisos limitados | Alto — bloquea deploy | Validar permisos en S1 día 1; plan B: deploy en cuenta personal |
| `ddl-auto: update` rompe schema en prod | Medio | Pasar a `validate` con baseline en S1 |
| Tests acumulados para S4 | Alto | Empezar tests durante S2/S3 en paralelo |
| Capacidad real menor a la estimada (TPOs paralelos) | Medio | Buffer de 12 días; cortar scope en S3 antes que sacrificar testing |
| Cambios de contrato frontend↔backend | Bajo-medio | Reviews cruzados cuando cambia un DTO |

---

## 10. Cadencia y ceremonias

- **Lunes 19hs:** sprint planning + reparto de tareas (30 min).
- **Miércoles 19hs:** sync corto, desbloquear, compartir contratos con otros módulos (15 min).
- **Viernes 19hs:** demo interna + retro (45 min).
- **Async diario:** un mensaje por persona en el canal del equipo (qué hice / qué voy a hacer / blockers).
- **Cross-grupos:** canal compartido con representantes de los demás módulos. Reuniones cross fijas: miércoles 21hs.

---

## 11. Definition of Done global (entrega final)

Para considerar el TPO entregado:
- [ ] Módulo desplegado en AWS, accesible vía URL pública.
- [ ] Telemetría fluyendo extremo a extremo (simulada o real).
- [ ] Alertas del motor de reglas visibles en UI y propagadas al Módulo 6.
- [ ] Autenticación JWT integrada con Módulo 10 (o mock formal acordado con la cátedra).
- [ ] Swagger + Postman + README + ARCHITECTURE + EVENTS_CATALOG entregados.
- [ ] Suite de tests pasa en CI; cobertura ≥60% en `service/` y `consumer/`.
- [ ] Demo y guion de defensa listos.
- [ ] Cada decisión técnica justificada en `docs/`.
