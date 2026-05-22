# 🔨 Guía de Desarrollo - Extensiones Futuras

## Agregar Nuevos Endpoints

### Paso 1: Crear DTO (si aplica)
```java
// src/main/java/com/healthgrid/monitoring/dto/YourDTO.java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Your DTO")
public class YourDTO {
    // Propiedades...
}
```

### Paso 2: Crear Model (si aplica)
```java
// src/main/java/com/healthgrid/monitoring/model/YourModel.java
@Entity
@Table(name = "your_table")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YourModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Propiedades...
}
```

### Paso 3: Crear Repository
```java
// src/main/java/com/healthgrid/monitoring/repository/YourRepository.java
@Repository
public interface YourRepository extends JpaRepository<YourModel, Long> {
    // Métodos custom...
}
```

### Paso 4: Crear Service
```java
// src/main/java/com/healthgrid/monitoring/service/YourService.java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class YourService {
    private final YourRepository repository;
    
    public YourDTO create(YourDTO dto) {
        log.info("Creating YourModel");
        // Implementar lógica...
    }
}
```

### Paso 5: Crear Controller
```java
// src/main/java/com/healthgrid/monitoring/controller/YourController.java
@RestController
@RequestMapping("/your-endpoint")
@RequiredArgsConstructor
@Tag(name = "Your Feature", description = "Your Feature Endpoints")
public class YourController {
    private final YourService service;
    
    @PostMapping
    @Operation(summary = "Create", description = "Create new record")
    public ResponseEntity<YourDTO> create(@Valid @RequestBody YourDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }
}
```

---

## Agregar Nuevos Consumers de Eventos

```java
// src/main/java/com/healthgrid/monitoring/consumer/YourEventConsumer.java
@Component
@Slf4j
@RequiredArgsConstructor
public class YourEventConsumer {
    private final YourService service;
    
    @Bean
    public Consumer<YourEventDTO> yourEventInput() {
        return event -> {
            try {
                log.info("Processing event: {}", event.getId());
                processEvent(event);
            } catch (Exception e) {
                log.error("Error processing event", e);
            }
        };
    }
    
    private void processEvent(YourEventDTO event) {
        // Implementar lógica...
    }
}
```

Agregar binding en `application.yml`:
```yaml
spring.cloud.stream.bindings:
  yourEventInput:
    destination: your-event-queue
    group: monitoring-service-group
```

---

## Agregar Tests

### Test Unitario
```java
// src/test/java/com/healthgrid/monitoring/service/YourServiceTest.java
@ExtendWith(MockitoExtension.class)
class YourServiceTest {
    @Mock
    private YourRepository repository;
    
    @InjectMocks
    private YourService service;
    
    @Test
    void testCreate() {
        // Implementar test...
    }
}
```

### Test Controller
```java
// src/test/java/com/healthgrid/monitoring/controller/YourControllerTest.java
@WebMvcTest(YourController.class)
class YourControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private YourService service;
    
    @Test
    void testCreate() throws Exception {
        // Implementar test...
    }
}
```

### Ejecutar Tests
```bash
mvn test
mvn test -Dtest=YourServiceTest
mvn clean test -DskipIntegrationTests=false
```

---

## Agregar Configuración Custom

```java
// src/main/java/com/healthgrid/monitoring/config/YourConfig.java
@Configuration
@ConfigurationProperties(prefix = "your.config")
@Data
public class YourConfig {
    private String property1;
    private Integer property2;
    
    @Bean
    public YourBean yourBean() {
        return new YourBean(property1, property2);
    }
}
```

En `application.yml`:
```yaml
your:
  config:
    property1: value1
    property2: 123
```

---

## Agregar Custom Queries

```java
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    // Query derivada
    List<Patient> findByStatusAndAgeGreaterThan(String status, Integer age);
    
    // JPQL
    @Query("SELECT p FROM Patient p WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Patient> findRecentByStatus(@Param("status") String status);
    
    // Native SQL
    @Query(value = "SELECT * FROM patients WHERE age > ?1", nativeQuery = true)
    List<Patient> findAdultPatients(Integer age);
}
```

---

## Integración con Base de Datos

### Crear Índice Adicional
```java
@Entity
@Table(name = "patients", indexes = {
    @Index(name = "idx_patient_id", columnList = "id"),
    @Index(name = "idx_patient_status", columnList = "status"),
    @Index(name = "idx_patient_mrn", columnList = "mrn")  // Nuevo índice
})
public class Patient { ... }
```

### Crear Migración (con Flyway)
```sql
-- src/main/resources/db/migration/V1__Initial_Schema.sql
CREATE TABLE patients (
    id BIGSERIAL PRIMARY KEY,
    mrn VARCHAR(50) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    age INTEGER NOT NULL,
    gender VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    diagnosis TEXT,
    room_number VARCHAR(50),
    bed_number VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Usar Profiles

### Desarrollo
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### Producción
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"
```

### En application.yml
```java
@Value("${app.environment:development}")
private String environment;
```

---

## Debugging

### Usar breakpoints
En VS Code: 
1. Poner puntos de ruptura en el código
2. F5 para iniciar debug
3. Usar Debug Console para valores

### Ver logs detallados
En `application-dev.yml`:
```yaml
logging:
  level:
    com.healthgrid.monitoring: DEBUG
    org.hibernate.SQL: DEBUG
```

---

## Buenas Prácticas

✅ Usar DTOs para separar layers  
✅ Transaccionalidad en operaciones críticas  
✅ Validación en Controller y Entity  
✅ Logging en niveles apropiados (INFO, DEBUG, ERROR)  
✅ Excepciones específicas en lugar de genéricas  
✅ Documentar con OpenAPI annotations  
✅ Tests para nuevas funcionalidades  
✅ Usar Optional en lugar de null checks  
✅ Evitar N+1 queries (usar fetch joins)  
✅ Mantener servicios sin estado (stateless)  

---

## Solución de Problemas

### Error: "Connection refused" en PostgreSQL
```bash
# Verificar que PostgreSQL está corriendo
docker ps | grep postgres

# Reiniciar postgres
docker restart postgres-monitoring
```

### Error: "Queue not found" en SQS
```yaml
# Agregar auto-create-queue en bindings
spring.cloud.stream.aws-sqs.auto-create-queue: true
```

### Error: "Annotation processing"
No es un error real, es una advertencia de compilación. Ignorar.

### Dependencias no se descargan
```bash
mvn clean
mvn dependency:resolve
mvn compile
```

---

## Referencias Útiles

- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [Jakarta Validation](https://jakarta.ee/specifications/bean-validation/3.0/)
- [Lombok](https://projectlombok.org/)

---

Éxito en tu desarrollo! 🚀
