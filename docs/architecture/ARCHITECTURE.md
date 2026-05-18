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
│   │   │   │   └── PatientEventConsumer.java          [Consumidor SQS]
│   │   │   └── config/
│   │   │       └── AwsSqsConfig.java                  [Configuración AWS]
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
│  (PostgreSQL, AWS SQS, Eventos)         │
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

### 2. Procesamiento de Eventos SQS

```
AWS SQS Queue (patient-events-queue)
    │
    ▼
Spring Cloud Stream
    │
    ├─ Binding: patientEventInput
    │
    ▼
PatientEventConsumer
    │
    ├─ Procesar evento (PatientDTO)
    ├─ Aplicar lógica según estado
    │
    ▼
Operaciones (alertas, actualizaciones, etc.)
```

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

## 📡 Integración con AWS SQS

### Configuración
```
Spring Cloud Stream
    │
    ├─ Binding de entrada: patientEventInput
    ├─ Destino: patient-events-queue
    │
    ▼
AwsSqsConfig
    │
    ├─ Cliente SQS (SDK v2)
    ├─ Endpoint override (LocalStack en dev)
    ├─ Credenciales configurables
    │
    ▼
LocalStack (desarrollo)  o  AWS SQS (producción)
```

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

### 5. **Consumer Pattern (Event-Driven)**
```java
@Bean
public Consumer<PatientDTO> patientEventInput() {
    return patientEvent -> processPatientEvent(patientEvent);
}
```

## 🌍 Profiles de Configuración

### Desarrollo (dev)
```yaml
ddl-auto: create-drop          # Reinicia BD cada ejecución
show-sql: true                 # Muestra SQL
logging: DEBUG                 # Logs detallados
endpoint: http://localhost:4566 # LocalStack
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
- Spring Cloud Stream: Abstracción de broker
- Fácil migración: SQS → Kafka → RabbitMQ
- Dead Letter Queue ready

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
