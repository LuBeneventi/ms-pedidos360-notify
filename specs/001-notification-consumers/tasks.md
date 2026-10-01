# Tasks: Consumidores de Notificaciones y Comprobantes (ms-pedidos360-notify)

**Feature**: `001-notification-consumers` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Este documento contiene el desglose secuencial y ejecutable de tareas para construir el microservicio de notificaciones y generación de comprobantes para Pedidos360.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicialización del proyecto Java 21 / Maven, dependencias, exclusiones y contenedores de desarrollo local.

- [X] T001 Inicializar proyecto Maven con Java 21 y dependencias Spring Boot 3.3.x (`spring-boot-starter-amqp`, `spring-boot-starter-mail`, `openpdf`, `caffeine`, `jackson-datatype-jsr310`, `spring-boot-starter-actuator`) en `pom.xml`
- [X] T002 [P] Configurar exclusiones de archivos de construcción, credenciales y configuraciones de IDE en `.gitignore`
- [X] T003 [P] Configurar orquestación de servicios locales para RabbitMQ y Mailpit en `docker-compose.yml`
- [X] T004 [P] Configurar propiedades de aplicación y externalización de variables de entorno para RabbitMQ y SMTP en `src/main/resources/application.yml`
- [X] T005 Crear la clase principal de arranque de Spring Boot en `src/main/java/com/pedidos360/notify/NotifyApplication.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura central de mensajería, sobre compartido y servicio de idempotencia que bloquean la implementación de las historias de usuario.

- [X] T006 Implementar modelo genérico de sobre de eventos `BaseEventEnvelope<T>` con campos `type`, `eventId`, `timestamp`, `traceId`, `correlationId` y `payload` en `src/main/java/com/pedidos360/notify/domain/envelope/BaseEventEnvelope.java`
- [X] T007 [P] Declarar topología de RabbitMQ (exchanges `cmd.direct` y `cmd.dlx`, colas `q.cmd.email`, `q.cmd.invoice`, `q.cmd.email.dlq`, `q.cmd.invoice.dlq`, bindings y convertidor Jackson) en `src/main/java/com/pedidos360/notify/config/RabbitMQConfig.java`
- [X] T008 [P] Configurar bean `JavaMailSender` y propiedades de transporte SMTP en `src/main/java/com/pedidos360/notify/config/MailConfig.java`
- [X] T009 Implementar servicio de idempotencia y deduplicación en memoria por `eventId` con ventana temporal en `src/main/java/com/pedidos360/notify/service/IdempotencyService.java`
- [X] T010 [P] Crear pruebas unitarias para verificación de deduplicación de `eventId` en `src/test/java/com/pedidos360/notify/service/IdempotencyServiceTest.java`

**Checkpoint**: Infraestructura base, topología de colas e idempotencia completadas y probadas.

---

## Phase 3: User Story 1 - Procesamiento y Envío de Notificaciones por Correo (Priority: P1) 🎯 MVP

**Goal**: Consumir mensajes desde `q.cmd.email`, validar contenido, despachar correos informativos vía SMTP, confirmar con ACK o enviar a `q.cmd.email.dlq` ante fallos persistentes.

**Independent Test**: Publicar un mensaje conforme a `email-command.json` en `q.cmd.email`; verificar el envío del correo en Mailpit y la emisión de ACK, así como NACK/DLQ ante desconexión forzada de SMTP.

### Implementation for User Story 1

- [X] T011 [P] [US1] Crear modelo de payload de correo `EmailPayload` con validaciones de campos requeridos (`to`, `subject`, `orderNumber`) en `src/main/java/com/pedidos360/notify/domain/model/EmailPayload.java`
- [X] T012 [US1] Implementar servicio de despacho de correos transaccionales `EmailSenderService` usando `JavaMailSender` y `MimeMessageHelper` en `src/main/java/com/pedidos360/notify/service/EmailSenderService.java`
- [X] T013 [US1] Implementar consumidor `EmailListener` sobre `q.cmd.email` con validación de idempotencia, llamada a `EmailSenderService`, propagación de MDC (`traceId`, `correlationId`) y control explícito de ACK/NACK en `src/main/java/com/pedidos360/notify/listener/EmailListener.java`
- [X] T014 [US1] Crear pruebas unitarias y de integración para `EmailListener` validando ACK, NACK hacia DLQ y deduplicación en `src/test/java/com/pedidos360/notify/listener/EmailListenerTest.java`

**Checkpoint**: Flujo de correos (User Story 1 - MVP) completamente operativo y verificable en Mailpit.

---

## Phase 4: User Story 2 - Generación de Boletas/Facturas en PDF y Entrega (Priority: P2)

**Goal**: Consumir comandos desde `q.cmd.invoice`, compilar la boleta/factura en memoria usando OpenPDF, enviarla como adjunto por correo reutilizando la configuración SMTP y gestionar ACK/NACK hacia `q.cmd.invoice.dlq`.

**Independent Test**: Publicar un mensaje conforme a `invoice-command.json` en `q.cmd.invoice`; verificar la recepción del correo en Mailpit con el archivo PDF adjunto estructurado con ítems y totales.

### Implementation for User Story 2

- [X] T015 [P] [US2] Crear modelos de datos de facturación `CustomerInfo`, `InvoiceItem` e `InvoicePayload` con reglas de validación en `src/main/java/com/pedidos360/notify/domain/model/InvoicePayload.java`
- [X] T016 [P] [US2] Crear modelo de documento generado `GeneratedPdfDocument` para encapsular bytes del PDF en memoria en `src/main/java/com/pedidos360/notify/domain/model/GeneratedPdfDocument.java`
- [X] T017 [US2] Implementar generador de PDF `InvoicePdfGenerator` con OpenPDF para maquetar membrete de Pedidos360, cliente, tabla de cafés y totales en `src/main/java/com/pedidos360/notify/service/InvoicePdfGenerator.java`
- [X] T018 [US2] Extender `EmailSenderService` para soportar envío de correos con archivos PDF adjuntos en memoria en `src/main/java/com/pedidos360/notify/service/EmailSenderService.java`
- [X] T019 [US2] Implementar consumidor `InvoiceListener` sobre `q.cmd.invoice` con validación de idempotencia, generación de PDF, envío de adjunto y manejo de ACK/NACK en `src/main/java/com/pedidos360/notify/listener/InvoiceListener.java`
- [X] T020 [P] [US2] Crear pruebas unitarias para `InvoicePdfGenerator` validando generación correcta de bytes y tablas en `src/test/java/com/pedidos360/notify/service/InvoicePdfGeneratorTest.java`
- [X] T021 [US2] Crear pruebas unitarias para `InvoiceListener` comprobando flujo exitoso con adjunto y derivación a DLQ ante fallos en `src/test/java/com/pedidos360/notify/listener/InvoiceListenerTest.java`

**Checkpoint**: Flujo de boletas y facturas PDF operativo, entregando adjuntos por correo y respondiendo ACK en `q.cmd.invoice`.

---

## Phase 5: User Story 3 - Deduplicación e Idempotencia por eventId (Priority: P3)

**Goal**: Garantizar que la re-entrega de cualquier mensaje con un `eventId` ya procesado en `q.cmd.email` o `q.cmd.invoice` sea ignorada de forma segura emitiendo ACK inmediato sin generar correos o PDFs duplicados.

**Independent Test**: Publicar dos veces consecutivas el mismo mensaje en cada cola y confirmar que solo la primera vez se realiza el envío en Mailpit y ambas transacciones reciben ACK.

### Implementation for User Story 3

- [X] T022 [US3] Extender lógica de registro de idempotencia en `IdempotencyService` para sincronización atómica concurrente y control por tipo de flujo en `src/main/java/com/pedidos360/notify/service/IdempotencyService.java`
- [X] T023 [US3] Crear prueba de integración de flujo de idempotencia y concurrencia verificando que mensajes duplicados en ambas colas generan ACK sin reprocesar en `src/test/java/com/pedidos360/notify/listener/IdempotencyFlowTest.java`

**Checkpoint**: Idempotencia demostrada y probada para re-entregas en ambos consumidores.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras transversales de observabilidad, trazabilidad distribuida y validación completa de la guía quickstart.

- [X] T024 [P] Implementar propagación de MDC en logs (`traceId`, `correlationId`, `eventId`) para seguimiento unificado en `src/main/java/com/pedidos360/notify/config/MdcLoggingAdvice.java`
- [X] T025 [P] Configurar endpoint de Actuator `/actuator/health` con indicador de conectividad a RabbitMQ en `src/main/resources/application.yml`
- [X] T026 Ejecutar y validar los 4 escenarios de la guía de inicio rápido en `specs/001-notification-consumers/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: Sin dependencias iniciales.
- **Phase 2 (Foundational)**: Depende de Phase 1. Bloquea todas las historias de usuario.
- **Phase 3 (User Story 1 - MVP)**: Depende de Phase 2. Puede entregarse de forma autónoma.
- **Phase 4 (User Story 2)**: Depende de Phase 2 y reutiliza el despacho SMTP de Phase 3.
- **Phase 5 (User Story 3)**: Valida la idempotencia de Phase 3 y Phase 4 de manera integral.
- **Phase 6 (Polish)**: Depende de la finalización de las historias de usuario.

### Parallel Opportunities
- T002, T003 y T004 se pueden ejecutar en paralelo dentro de Phase 1.
- T007 y T008 se pueden ejecutar en paralelo dentro de Phase 2.
- T015 y T016 se pueden ejecutar en paralelo dentro de Phase 4.
- T024 y T025 se pueden ejecutar en paralelo dentro de Phase 6.

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Completar Phase 1 (Setup) y Phase 2 (Foundational).
2. Implementar Phase 3 (User Story 1).
3. Validar con Mailpit y RabbitMQ que los correos de `q.cmd.email` se despachan con ACK/NACK hacia DLQ.
4. Con esto se tiene un MVP funcional desplegable.

### Entrega Incremental
1. Phase 1 + Phase 2 $\rightarrow$ Base de mensajería lista.
2. Phase 3 $\rightarrow$ Notificaciones operativas (**MVP**).
3. Phase 4 $\rightarrow$ Generación de comprobantes PDF y adjuntos.
4. Phase 5 $\rightarrow$ Robustecimiento de idempotencia ante re-entregas.
5. Phase 6 $\rightarrow$ Trazabilidad y validación end-to-end.
