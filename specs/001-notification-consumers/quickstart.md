# Quickstart & Validation Guide: Consumidores de Notificaciones y Comprobantes

**Feature**: `001-notification-consumers` | **Date**: 2026-10-01

Esta guía describe el procedimiento para levantar la infraestructura de soporte (RabbitMQ y servidor SMTP simulado), iniciar el microservicio `ms-pedidos360-notify` y validar de extremo a extremo los flujos de correo, facturación en PDF, reintentos/DLQ e idempotencia.

---

## 1. Prerrequisitos

- **Java**: OpenJDK 21 LTS instalado (`java -version`).
- **Maven**: 3.9+ (`mvn -version`) o Maven Wrapper (`mvnw`).
- **Docker & Docker Compose**: Para ejecutar RabbitMQ y Mailpit/MailHog localmente.

---

## 2. Levantar Infraestructura Local

Crear y ejecutar el entorno de contenedores en segundo plano:

```bash
docker compose up -d
```

Servicios expuestos:
- **RabbitMQ**:
  - AMQP: `localhost:5672`
  - Management UI: `http://localhost:15672` (usuario: `guest`, contraseña: `guest`)
- **Mailpit / MailHog**:
  - SMTP: `localhost:1025`
  - Web UI: `http://localhost:8025`

---

## 3. Variables de Entorno y Ejecución del Microservicio

El servicio no requiere base de datos y toma la configuración por variables de entorno con valores por defecto locales:

```bash
# Variables opcionales para personalizar (valores por defecto ya configurados en application.yml)
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export SMTP_HOST=localhost
export SMTP_PORT=1025

# Iniciar la aplicación Spring Boot
mvn spring-boot:run
```

Al iniciar, el microservicio declarará automáticamente:
- Exchanges: `cmd.direct` y `cmd.dlx`
- Colas: `q.cmd.email`, `q.cmd.invoice`, `q.cmd.email.dlq`, `q.cmd.invoice.dlq`
- Bindings correspondientes

---

## 4. Escenarios de Validación End-to-End

### Escenario 1: Flujo Exitoso de Envío de Correo (`q.cmd.email`)
1. Publicar un mensaje conforme al contrato [email-command.json](contracts/email-command.json) en el exchange `cmd.direct` con routing key `cmd.email` (a través de la UI de RabbitMQ o CLI `rabbitmqadmin`).
2. **Resultado Esperado**:
   - En la consola de la aplicación se visualiza el log con `eventId`, `traceId` y confirmación de envío.
   - La cola `q.cmd.email` reduce su tamaño a 0 (mensaje confirmado con **ACK**).
   - En la interfaz web de Mailpit (`http://localhost:8025`) aparece el correo recibido con destinatario, asunto y cuerpo correspondientes.

### Escenario 2: Flujo Exitoso de Boleta/Factura PDF (`q.cmd.invoice`)
1. Publicar un mensaje conforme al contrato [invoice-command.json](contracts/invoice-command.json) en `cmd.direct` con routing key `cmd.invoice`.
2. **Resultado Esperado**:
   - `InvoicePdfGenerator` compila el documento en memoria con el encabezado de Pedidos360, cliente, tabla de cafés y totales.
   - El correo es despachado con el PDF adjunto (`Boleta_BOL-2026-00451.pdf`).
   - Se emite **ACK** en `q.cmd.invoice`.
   - En Mailpit (`http://localhost:8025`), el correo contiene el archivo PDF adjunto legible con el desglose exacto de los ítems.

### Escenario 3: Idempotencia por `eventId`
1. Re-publicar el **mismo mensaje exacto** (con idéntico `eventId`) enviado en el Escenario 1 o 2.
2. **Resultado Esperado**:
   - `IdempotencyService` detecta que el `eventId` ya fue procesado.
   - El servicio emite un log informativo indicando descarte por duplicidad.
   - El mensaje se confirma con **ACK** inmediato.
   - **NO se despacha ningún correo adicional** ni se genera un nuevo PDF (comprobado en Mailpit).

### Escenario 4: Reintentos y Derivación a DLQ (`q.cmd.*.dlq`)
1. Detener temporalmente el contenedor de Mailpit (`docker stop <mailpit-container>`).
2. Publicar un nuevo mensaje en `q.cmd.email` con un `eventId` nuevo.
3. **Resultado Esperado**:
   - El servicio falla al intentar conectar al puerto SMTP 1025.
   - Se ejecutan los reintentos configurados con backoff.
   - Al agotarse los reintentos, el listener emite **NACK** sin requeue.
   - El mensaje es transferido por RabbitMQ a `q.cmd.email.dlq`.
   - La cola `q.cmd.email` queda en 0 y `q.cmd.email.dlq` incrementa su contador en 1, garantizando cero pérdida de mensajes.

---

## 5. Ejecución de Pruebas Automatizadas

```bash
# Ejecutar suite de pruebas unitarias y de integración (usando Testcontainers)
mvn clean test
```
