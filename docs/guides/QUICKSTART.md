# Patient Monitoring Service - Guía de Instalación Rápida

## 🚀 Inicio Rápido

### Opción 1: Con Docker (Recomendado)

```bash
# Levantar PostgreSQL y LocalStack
docker-compose up -d

# Compilar y ejecutar la aplicación
mvn clean spring-boot:run
```

### Opción 2: Manual

#### 1. Preparar PostgreSQL
```bash
# Si tienes PostgreSQL instalado localmente
psql -U postgres -c "CREATE DATABASE monitoring_db;"

# O con Docker
docker run --name postgres-monitoring -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:15
```

#### 2. Preparar AWS SQS (LocalStack)
```bash
docker run --name localstack -p 4566:4566 -e SERVICES=sqs -d localstack/localstack
```

#### 3. Compilar y Ejecutar
```bash
mvn clean install
mvn spring-boot:run
```

## 📋 Verificación de Instalación

1. **API Health Check**
```bash
curl http://localhost:8080/api/v1/actuator/health
```

2. **Acceder a Swagger UI**
```
http://localhost:8080/api/v1/swagger-ui.html
```

3. **Crear un Paciente de Prueba**
```bash
curl -X POST http://localhost:8080/api/v1/patients \
  -H "Content-Type: application/json" \
  -d '{
    "mrn": "MRN-TEST-001",
    "firstName": "Test",
    "lastName": "Patient",
    "age": 30,
    "gender": "M",
    "status": "ADMITTED",
    "diagnosis": "Testing",
    "roomNumber": "101",
    "bedNumber": "A"
  }'
```

## 🔧 Configuración

Editar `src/main/resources/application.yml`:

```yaml
# PostgreSQL
spring.datasource.url: jdbc:postgresql://localhost:5432/monitoring_db
spring.datasource.username: postgres
spring.datasource.password: postgres

# AWS SQS / LocalStack
aws.sqs.endpoint: http://localhost:4566
aws.sqs.region: us-east-1
```

## 📦 Dependencias Instaladas

- ✅ Spring Boot 3.3
- ✅ Spring Data JPA
- ✅ PostgreSQL Driver
- ✅ Lombok
- ✅ Spring Cloud Stream (AWS SQS)
- ✅ SpringDoc OpenAPI (Swagger)
- ✅ Spring Boot Validation

## 🛑 Detener Servicios

```bash
docker-compose down
```

## 📚 Recursos Útiles

- [Documentación Spring Boot 3.3](https://spring.io/projects/spring-boot)
- [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [LocalStack](https://www.localstack.cloud/)

---

Para más detalles, ver `README.md`
