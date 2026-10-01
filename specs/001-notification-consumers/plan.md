# Implementation Plan: Consumidores de Notificaciones y Comprobantes (ms-pedidos360-notify)

**Branch**: `001-notification-consumers` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-notification-consumers/spec.md`

## Summary

Implementar dos consumidores RabbitMQ en el microservicio `ms-pedidos360-notify` (Java 21, Spring Boot 3.3.x):
1. `EmailListener`: Procesa comandos de notificación desde la cola `q.cmd.email`, deserializando un sobre genérico `BaseEventEnvelope<EmailPayload>` y despachando correos transaccionales vía `JavaMailSender`.
2. `InvoiceListener`: Procesa comandos de facturación desde la cola `q.cmd.invoice`, deserializando `BaseEventEnvelope<InvoicePayload>`, compilando en memoria un comprobante tributario (boleta/factura) en formato PDF mediante OpenPDF (`com.github.librepdf:openpdf`) y enviándolo como adjunto al cliente usando la misma infraestructura SMTP.
3. Ambos flujos implementan deduplicación e idempotencia estricta por `eventId` mediante `IdempotencyService` en memoria, gestión explícita de confirmaciones ACK/NACK con reintentos controlados y enrutamiento hacia colas de descarte DLQ (`q.cmd.email.dlq`, `q.cmd.invoice.dlq`) en caso de errores irrecuperables.

## Technical Context

**Language/Version**: Java 21 (OpenJDK 21 LTS)

**Primary Dependencies**:
- Spring Boot 3.3.x
- `spring-boot-starter-amqp` (Spring AMQP / RabbitMQ)
- `spring-boot-starter-mail` (JavaMailSender / Jakarta Mail)
- `com.github.librepdf:openpdf:2.0.3` (Generador de PDF bajo licencia LGPL/MPL)
- `spring-boot-starter-actuator` (Endpoints de salud y métricas)
- `com.github.ben-manes.caffeine:caffeine` (Caché en memoria de alto rendimiento para idempotencia)
- `jackson-databind` y `jackson-datatype-jsr310` (Serialización JSON y fechas ISO-8601)

**Storage**: N/A (Stateless: Sin base de datos relacional ni NoSQL persistente, conforme a la Constitución del microservicio. Deduplicación efímera en memoria con TTL de 24 horas).

**Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers RabbitMQ, GreenMail (para pruebas SMTP en memoria).

**Target Platform**: Servidor Linux / Docker Container / Kubernetes.

**Project Type**: Asynchronous Event Consumer Microservice (sin API REST de negocio expuesta).

**Performance Goals**:
- Procesamiento y envío de correo: < 5 segundos por mensaje.
- Generación de PDF y envío con adjunto: < 8 segundos por mensaje.
- Consumo con cero duplicados ante re-entregas de RabbitMQ.

**Constraints**:
- Confirmación explícita ACK en procesamiento exitoso; NACK sin requeue hacia DLQ al agotar reintentos.
- Cero credenciales hardcodeadas: Configuración SMTP externalizada (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`).
- Configuración RabbitMQ externalizada (`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`).
- Exclusión estricta en `.gitignore` de `target/`, `.idea/`, `.vscode/` y variables de entorno.

**Scale/Scope**:
- 2 colas principales (`q.cmd.email`, `q.cmd.invoice`), 2 DLQ (`q.cmd.email.dlq`, `q.cmd.invoice.dlq`).
- Soporte para miles de notificaciones diarias de venta de café.

## Constitution Check

*GATE: Evaluado antes de diseño y re-verificado post-diseño.*

| Principio Constitucional | Estado | Justificación / Alineación Técnica |
|---|---|---|
| **I. Spring Boot 3+ y Java 21** | PASS | El proyecto se configura con Java 21 y Spring Boot 3.3.x en `pom.xml`. |
| **II. Consumidor RabbitMQ sin API REST de Negocio** | PASS | No se exponen `@RestController` de negocio; solo `@RabbitListener` y Actuator opcional para `/health`. |
| **III. Stateless: Sin Base de Datos Propia** | PASS | No se configuran JPA, Hibernate ni datasources relacionales/NoSQL. |
| **IV. Idempotencia Estricta por eventId** | PASS | `IdempotencyService` compartido valida `eventId` en memoria; si existe, emite ACK inmediato sin reprocesar. |
| **V. ACK/NACK Explícitos y DLQ** | PASS | Configuración de dead lettering hacia `cmd.dlx` y colas DLQ ante NACK/fallos terminales. |
| **VI. Configuración SMTP Externalizada** | PASS | Valores inyectados mediante `${SMTP_HOST:localhost}`, `${SMTP_PORT:1025}`, etc. |
| **VII. Desacoplamiento de Boletas/Facturas (PDF)** | PASS | `InvoicePdfGenerator` genera el PDF desacoplado y el servicio de entrega lo adjunta al correo del cliente. |
| **VIII. Higiene de Repositorio Java/Maven** | PASS | Estructura estándar Maven, archivo `.gitignore` estricto excluyendo artefactos y credenciales. |

## Project Structure

### Documentation (this feature)

```text
specs/001-notification-consumers/
├── spec.md              # Requisitos y casos de uso del usuario
├── plan.md              # Este plan de implementación técnica
├── research.md          # Investigación técnica y decisiones (OpenPDF, RabbitMQ, Idempotencia)
├── data-model.md        # Definición de DTOs, Envelope y modelos
├── quickstart.md        # Guía paso a paso de ejecución y pruebas locales E2E
├── contracts/           # Contratos JSON de mensajería
│   ├── email-command.json
│   └── invoice-command.json
└── checklists/
    └── requirements.md  # Validación de calidad de la especificación
```

### Source Code (repository root)

```text
.
├── .gitignore
├── pom.xml
├── docker-compose.yml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── pedidos360/
    │   │           └── notify/
    │   │               ├── NotifyApplication.java
    │   │               ├── config/
    │   │               │   ├── RabbitMQConfig.java
    │   │               │   └── MailConfig.java
    │   │               ├── domain/
    │   │               │   ├── envelope/
    │   │               │   │   └── BaseEventEnvelope.java
    │   │               │   └── model/
    │   │               │       ├── EmailPayload.java
    │   │               │       ├── InvoicePayload.java
    │   │               │       ├── CustomerInfo.java
    │   │               │       ├── InvoiceItem.java
    │   │               │       └── GeneratedPdfDocument.java
    │   │               ├── listener/
    │   │               │   ├── EmailListener.java
    │   │               │   └── InvoiceListener.java
    │   │               └── service/
    │   │                   ├── IdempotencyService.java
    │   │                   ├── EmailSenderService.java
    │   │                   └── InvoicePdfGenerator.java
    │   └── resources/
    │       └── application.yml
    └── test/
        ├── java/
        │   └── com/
        │       └── pedidos360/
        │           └── notify/
        │               ├── listener/
        │               │   ├── EmailListenerTest.java
        │               │   └── InvoiceListenerTest.java
        │               ├── service/
        │               │   ├── IdempotencyServiceTest.java
        │               │   └── InvoicePdfGeneratorTest.java
        │               └── integration/
        │                   └── RabbitMQIntegrationTest.java
        └── resources/
            └── application-test.yml
```

**Structure Decision**:
Se adopta la estructura estándar de microservicio Java/Maven con Spring Boot en paquete raíz `com.pedidos360.notify`. La lógica de listeners, servicios y dominio se separa limpiamente por responsabilidades:
- `config`: Declaración de beans de RabbitMQ (colas, exchanges, bindings) y configuración de correo.
- `domain`: Envelope genérico y modelos inmutables de payload.
- `listener`: Consumidores `@RabbitListener` para cada cola con control de flujo y ACK.
- `service`: Idempotencia, despacho SMTP y generación de PDF.

## Complexity Tracking

*No se registran violaciones a la Constitución del proyecto. No se requiere tracking de complejidad adicional.*
