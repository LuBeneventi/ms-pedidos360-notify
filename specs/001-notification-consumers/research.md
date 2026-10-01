# Research & Technical Decisions: Consumidores de Notificaciones y Comprobantes

**Feature**: `001-notification-consumers` | **Date**: 2026-10-01

Este documento consolida las evaluaciones técnicas, selección de bibliotecas y patrones arquitectónicos para implementar los consumidores del microservicio `ms-pedidos360-notify`.

---

## 1. Selección de Biblioteca para Generación de PDF

### Decisión
Se adopta **OpenPDF** (`com.github.librepdf:openpdf:2.0.3` o versión actual compatible con Java 21).

### Rationale
- **Licencia**: OpenPDF se distribuye bajo licencias LGPL y MPL (Mozilla Public License), lo que permite su uso libre y comercial en microservicios backend sin imponer las restricciones víricas o requisitos de licenciamiento comercial privativo de otras herramientas.
- **Rendimiento y Sencillez**: Es una evolución y bifurcación limpia y mantenida de iText (versiones 2.x/4.x), optimizada para Java moderno. Provee abstracciones de alto nivel (`Document`, `PdfPTable`, `PdfPCell`, `Paragraph`, fuentes e imágenes) ideales para maquetar boletas y facturas de venta de café con tablas de ítems, totales desglosados y encabezados corporativos.
- **Generación en Memoria**: Genera documentos en `ByteArrayOutputStream` directamente, permitiendo inyectar el flujo de bytes (`ByteArrayResource`) en `MimeMessageHelper` de Spring Mail sin tocar el disco ni requerir almacenamiento persistente.

### Alternativas Consideradas
- **iText 7 / iText 8**: Rechazado por su licencia AGPL v3 (requiere liberar todo el código del ecosistema o pagar licencias comerciales de alto costo).
- **Apache PDFBox**: Aunque tiene licencia Apache 2.0, opera a muy bajo nivel (dibujo de primitivas por coordenadas X, Y, manipulación manual de saltos de línea y celdas), lo que incrementa sustancialmente la complejidad y el mantenimiento del maquetado de tablas de facturación.
- **Flying Saucer / Thymeleaf-to-PDF**: Requiere parseo XHTML y motores de renderizado web, introduciendo dependencias pesadas innecesarias para un comprobante estructurado.

---

## 2. Modelado de Envelope Compartido y Deserialización de Mensajes

### Decisión
Se implementa una estructura genérica de sobre `BaseEventEnvelope<T>` compatible con Jackson (Spring Boot starter JSON):
- `type`: Identificador de tipo de mensaje/comando (ej. `cmd.email.send`, `cmd.invoice.generate`).
- `eventId`: `String` (o `UUID`) único por evento generado.
- `timestamp`: `Instant` o `OffsetDateTime` (formato ISO-8601).
- `traceId`: `String` para observabilidad distribuida.
- `correlationId`: `String` para correlación de transacciones/sagas.
- `payload`: Genérico `T` (`EmailPayload`, `InvoicePayload`).

### Rationale
- Mantiene la consistencia de metadatos de telemetría y trazabilidad a lo largo de toda la arquitectura de Pedidos360.
- Permite propagar los identificadores `traceId` y `correlationId` al contexto de logs (MDC) durante el ciclo de vida del procesamiento de cada mensaje.

### Alternativas Consideradas
- **Headers AMQP nativos**: Aunque RabbitMQ permite cabeceras en los `MessageProperties`, ubicar los metadatos en el cuerpo JSON (envelope) garantiza portabilidad agnóstica del mensaje y consistencia de schemas documentados en contratos JSON.

---

## 3. Idempotencia y Deduplicación sin Base de Datos

### Decisión
Se implementa un servicio de idempotencia en memoria (`IdempotencyService`) utilizando una estructura concurrente con ventana de tiempo y capacidad acotada:
- Implementación con **Caffeine Cache** (o `ConcurrentHashMap` con purga basada en TTL/LRU, e.g. retención de 24 horas por `eventId`).
- Operación atómica `putIfAbsent(eventId, Instant.now())`. Si el ID ya existe, se clasifica como duplicado.

### Rationale
- Cumple estrictamente con el **Principio III (Stateless / Sin DB propia)** de la Constitución.
- El 99% de las re-entregas en RabbitMQ ocurren en una ventana de segundos o minutos (redeliveries inmediatas por reconexión o reintentos de red). Una ventana en memoria de 24 horas con límite de entradas (ej. 100,000 IDs ~ 10 MB de memoria heap) mitiga el impacto de duplicados sin sobrecargar el microservicio.
- Si en el futuro se requiriera idempotencia distribuida multinodo persistente, la interfaz `IdempotencyRepository` podrá desacoplarse a un store compartido (ej. Redis/Valkey) sin modificar los listeners.

### Alternativas Consideradas
- **Base de datos relacional (H2 / PostgreSQL)**: Prohibido explícitamente por el Principio III de la Constitución.
- **Sin validación (confiar en la red)**: Viola el Principio IV y genera envíos duplicados de correos y facturas a los clientes.

---

## 4. Topología RabbitMQ, Manejo de ACK/NACK y Dead Letter Queue (DLQ)

### Decisión
Se declaran programmaticamente mediante beans de Spring AMQP:
- **Exchange Principal**: `cmd.direct` (DirectExchange).
- **Exchange de DLQ**: `cmd.dlx` (DirectExchange).
- **Colas Principales**:
  - `q.cmd.email`: Enrutada con binding `cmd.email`. Argumentos de DLQ: `x-dead-letter-exchange=cmd.dlx`, `x-dead-letter-routing-key=cmd.email.dlq`.
  - `q.cmd.invoice`: Enrutada con binding `cmd.invoice`. Argumentos de DLQ: `x-dead-letter-exchange=cmd.dlx`, `x-dead-letter-routing-key=cmd.invoice.dlq`.
- **Colas DLQ**:
  - `q.cmd.email.dlq` con binding `cmd.email.dlq` en `cmd.dlx`.
  - `q.cmd.invoice.dlq` con binding `cmd.invoice.dlq` en `cmd.dlx`.
- **Estrategia de Reintentos y ACK**:
  - Configuración de retry con backoff exponencial en `RabbitListenerContainerFactory` o captura explícita con `Channel.basicAck` / `Channel.basicNack(requeue = false)`.
  - Al agotarse los reintentos (o ante fallo fatal de validación), `requeue = false` enruta el mensaje automáticamente a la DLQ correspondiente gracias a la configuración de dead-lettering de RabbitMQ.

### Rationale
- Cumple de manera nativa con el **Principio V (ACK/NACK explícitos y DLQ)**.
- El dead lettering a nivel de broker garantiza que los mensajes tóxicos o irrecuperables no queden bloqueando la cabeza de la cola ni se pierdan silenciosamente.

---

## 5. Configuración de Envío de Correo (SMTP / JavaMailSender)

### Decisión
Se configura `JavaMailSender` en `application.yml` mediante propiedades externalizadas:
```yaml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${SMTP_AUTH:false}
          starttls:
            enable: ${SMTP_STARTTLS:false}
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
```

### Rationale
- En desarrollo local apunta a **MailHog / Mailpit** (puerto SMTP 1025, interfaz web 8025) sin necesidad de credenciales ni riesgo de enviar correos reales por error.
- En producción o staging se sobreescriben `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH` y `SMTP_STARTTLS` mediante variables de entorno, cumpliendo rigurosamente el **Principio VI**.
