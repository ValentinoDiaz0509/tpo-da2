# Monitoreo de Pacientes - Arquitetura del Proyecto

## 📐 Estructura General

```
monitoring-service/
├── src/
│   ├── main/
│   │   ├── java/com/healthgrid/monitoring/
│   │   │   ├── MonitoringServiceApplication.java      [Clase Principal]
│   │   │   ├── controller/
│   │   │   │   └── PatientController.java             [REST Endpoints]
│   │   │   ├── service/
│   │   │   │   └── PatientService.java                [Lógica de Negocio]
│   │   │   ├── repository/
│   │   │   │   └── PatientRepository.java             [Acceso a Datos]
│   │   │   ├── model/
│   │   │   │   └── Patient.java                       [Entidad JPA]
│   │   │   ├── dto/
│   │   │   │   └── PatientDTO.java                    [Transfer Object]
│   │   │   ├── consumer/
│   │   │   │   ├── AdmissionEventListener.java        [Listener RabbitMQ - Core bus]
│   │   │   │   └── TelemetryConsumer.java             [Procesa telemetría interna]
│   │   │   └── config/
│   │   │       └── JacksonConfig.java                 [ObjectMapper compartido]
│   │   └── resources/
│   │       ├── application.yml                        [Config del servidor]
│   │       ├── application-dev.yml                    [Config desarrollo]
│   │       └── application-prod.yml                   [Config producción]
│   └── test/
│       └── java/com/healthgrid/monitoring/
│           ├── service/PatientServiceTest.java       [Test del Servicio]
│           ├── controller/PatientControllerTest.java [Test del Controller]
│           └── BaseIntegrationTest.java              [Test Base]
├── pom.xml                                            [Configuración Maven]
├── docker-compose.yml                                 [Orquestación Docker]
├── README.md                                          [Documentación]
├── QUICKSTART.md                                      [Inicio Rápido]
├── ARCHITECTURE.md                                    [Este archivo]
├── requests.http                                      [Ejemplos HTTP]
└── .gitignore                                         [Git Config]
```

## 🏗️ Arquitectura en Capas

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│    (PatientController - REST API)       │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│         Business Logic Layer            │
│      (PatientService - Servicios)       │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│     Data Access Layer                   │
│  (PatientRepository - Acceso a Datos)   │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│      Infrastructure Layer               │
│  (PostgreSQL, RabbitMQ/Core, Eventos)   │
└─────────────────────────────────────────┘
```

## 🔄 Flujo de Datos

### 1. Crear un Paciente

```
Cliente HTTP
    │
    ├─ POST /patients (PatientDTO)
    │
    ▼
PatientController
    │
    ├─ Validar datos (Jakarta Validation)
    │
    ▼
PatientService
    │
    ├─ Crear entidad Patient
    ├─ Aplicar lógica de negocio
    │
    ▼
PatientRepository (JPA)
    │
    ├─ Persistir en PostgreSQL
    │
    ▼
Respuesta HTTP 201 (PatientDTO)
```

### 2. Procesamiento de Eventos de Admisión (RabbitMQ · Core bus)

```
Módulo 6 (Internación)
    │
    ├─ POST /events/log  (al Core, M10)
    │
    ▼
Core Event Bus (RabbitMQ · health_grid_exchange)
    │
    ├─ routing → cola monitoring.requests
    │
    ▼
AdmissionEventListener  (@RabbitListener)
    │
    ├─ Parsear sobre externo (CoreEventEnvelope)
    ├─ Parsear payload interno (string JSON → MonitoreoWebhookRequestDTO)
    │
    ▼
MonitoringAdmissionService
    │
    ├─ alta  → crea/reactiva paciente
    └─ baja  → marca paciente INACTIVE
```

> Las colas, eventos y bindings se aprovisionan desde el Core (`/rabbit/queues`, `/events/types`, `/rabbit/bindings`); M9 solo escucha su cola. El webhook REST `POST /webhooks/internacion/*` sigue activo en paralelo durante la transición.

## 📊 Entidades y Relaciones

### Patient Entity
```
┌─────────────────────────────┐
│        Patient              │
├─────────────────────────────┤
│ - id: Long (PK)             │
│ - mrn: String (UNIQUE)      │
│ - firstName: String         │
│ - lastName: String          │
│ - age: Integer              │
│ - gender: String            │
│ - status: String            │
│ - diagnosis: String         │
│ - roomNumber: String        │
│ - bedNumber: String         │
│ - notes: String             │
│ - createdAt: LocalDateTime  │
│ - updatedAt: LocalDateTime  │
└─────────────────────────────┘
```

## 🔐 Capas de Seguridad

### 1. Validación en Controller
- Anotaciones `@Valid` en parámetros
- Jakarta Bean Validation (antiguo javax.validation)

### 2. Validación en Entity
- Anotaciones `@NotBlank`, `@NotNull`, `@Min`, etc.
- Restricciones de base de datos (unique, not null)

### 3. Acceso a Datos
- Spring Data JPA con métodos especializados
- Transaccionalidad declarativa con `@Transactional`

## 📡 Integración con el Core (M10) vía RabbitMQ

### Entrada (escuchar)
```
spring-boot-starter-amqp
    │
    ├─ @RabbitListener → cola monitoring.requests
    ├─ host: queue.healthgrid.cantero.ar (spring.rabbitmq.*)
    ├─ requeue-rejected=false → mensaje fallido va a la DLQ
    │
    ▼
RabbitMQ del Core (health_grid_exchange · topic)
```

### Salida (publicar)
```
CoreAuthService  (login a /auth/login, token 24h cacheado)
    │
    ▼
CoreEventPublisher → POST /events/log
    ├─ event_type_id 16 → internacion.alerta-emergencia.detectada
    ├─ event_type_id 17 → internacion.alerta-resuelta.notificada
    └─ payload como string JSON
    │
    ▼
Core enruta el evento a internacion.requests (M6)
```

> No hay clientes de AWS en el código: la mensajería es 100 % RabbitMQ gestionado por el Core. En desarrollo se puede levantar un `rabbitmq:3-management` local (ver `backend/docker-compose.yml`).

## 🎯 Patrones Implementados

### 1. **Dependency Injection**
```java
@Service
@RequiredArgsConstructor
public class PatientService {
    private final PatientRepository patientRepository;  // Inyectado por Lombok
}
```

### 2. **Data Transfer Objects (DTO)**
```
Patient (Entidad JPA)
    │
    ├─ Mapeo <─── convertToDTO()
    │
    ▼
PatientDTO (Transfer Object)
```

### 3. **Repository Pattern**
```java
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByMrn(String mrn);
    List<Patient> findByStatus(String status);
}
```

### 4. **Service Pattern**
```java
@Service
@Transactional
public class PatientService {
    public PatientDTO createPatient(PatientDTO dto) { ... }
    public PatientDTO updatePatient(Long id, PatientDTO dto) { ... }
}
```

### 5. **Listener Pattern (Event-Driven · RabbitMQ)**
```java
@RabbitListener(queues = "${healthgrid.rabbit.monitoring-queue:monitoring.requests}")
public void onMessage(String rawMessage) {
    CoreEventEnvelope envelope = objectMapper.readValue(rawMessage, CoreEventEnvelope.class);
    MonitoreoWebhookRequestDTO admission =
        objectMapper.readValue(envelope.getPayload(), MonitoreoWebhookRequestDTO.class);
    admissionService.handleEvent(admission);
}
```

## 🌍 Profiles de Configuración

### Desarrollo (dev)
```yaml
ddl-auto: create-drop          # Reinicia BD cada ejecución
show-sql: true                 # Muestra SQL
logging: DEBUG                 # Logs detallados
rabbitmq.host: localhost       # RabbitMQ local (docker-compose)
```

### Producción (prod)
```yaml
ddl-auto: validate             # Solo validación
show-sql: false                # Sin SQL logging
logging: WARN                  # Solo advertencias
credentials: Variables de entorno
```

## 📈 Escalabilidad

### Base de Datos
- Connection Pool: HikariCP (10 máximo por defecto)
- Índices en: id, status
- Preparado para sharding

### Mensajería
- RabbitMQ gestionado por el Core (M10) como bus central de eventos
- Colas/eventos/bindings self-service vía la API del Core
- Dead Letter Queue por cola (creada por el Core)

### API
- Compresión HTTP habilitada
- Actuator para métricas
- OpenAPI/Swagger documentada

## 🚀 Próximas Mejoras

1. **Seguridad**
   - Spring Security + JWT
   - OAuth2 con Okta/Auth0
   - Rate limiting

2. **Cache**
   - Redis con Spring Cache
   - Caché de pacientes frecuentes

3. **Búsqueda Avanzada**
   - Elasticsearch
   - Criterios dinámicos

4. **Persistencia**
   - Flyway para migraciones
   - Auditoría de cambios

5. **Observabilidad**
   - Prometheus + Grafana
   - Jaeger para tracing distribuido
   - ELK Stack para logs

6. **Asincronía**
   - Completable Futures
   - Project Reactor (Reactive)

---

**Última actualización**: Marzo 2026  
**Versión**: 1.0.0  
**Estado**: En Desarrollo
