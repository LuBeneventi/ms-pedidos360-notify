# Data Model: Consumidores de Notificaciones y Comprobantes

**Feature**: `001-notification-consumers` | **Date**: 2026-10-01

Este documento define las entidades del dominio de mensajería, esquemas de transferencia de datos (DTOs) y modelos en memoria para `ms-pedidos360-notify`.

---

## 1. Esquema de Sobre Compartido (`BaseEventEnvelope<T>`)

Representa el contenedor común para todos los mensajes que circulan en el bus de eventos de Pedidos360.

| Campo | Tipo | Requerido | Descripción | Ejemplo |
|---|---|---|---|---|
| `type` | String | Sí | Tipo de evento o comando | `"cmd.email.send"` / `"cmd.invoice.generate"` |
| `eventId` | String / UUID | Sí | Identificador único global del mensaje | `"550e8400-e29b-41d4-a716-446655440000"` |
| `timestamp` | String (ISO-8601) | Sí | Momento en el que se generó el mensaje | `"2026-10-01T15:30:00Z"` |
| `traceId` | String | Sí | ID de traza distribuida para observabilidad | `"4bf92f3577b34da6a3ce929d0e0e4736"` |
| `correlationId` | String | Sí | ID de correlación de la saga o flujo de negocio | `"corr-pedidos-8921"` |
| `payload` | `T` | Sí | Carga útil especializada según el tipo | Objeto `EmailPayload` o `InvoicePayload` |

---

## 2. Modelos de Notificación por Correo (`q.cmd.email`)

### `EmailPayload`
Carga útil para comandos de envío de correos electrónicos informativos y transaccionales.

| Campo | Tipo | Requerido | Descripción | Ejemplo |
|---|---|---|---|---|
| `to` | String | Sí | Dirección de correo electrónico del destinatario | `"cliente.cafe@example.com"` |
| `subject` | String | Sí | Asunto del mensaje de correo | `"Tu pedido #PED-1024 ha sido confirmado"` |
| `orderNumber` | String | Sí | Identificador del pedido relacionado | `"PED-1024"` |
| `customerName` | String | Opcional | Nombre del cliente para personalización | `"Sofía Morales"` |
| `newStatus` | String | Opcional | Nuevo estado del pedido | `"CONFIRMADO"` / `"EN_PREPARACION"` / `"DESPACHADO"` |
| `body` | String | Opcional | Texto plano o resumen del cuerpo del mensaje | `"Tu café de especialidad está siendo tostado y preparado..."` |
| `templateParams`| Map<String, Object> | Opcional | Parámetros dinámicos adicionales para la plantilla | `{"trackingUrl": "https://..."}` |

---

## 3. Modelos de Facturación y Comprobantes (`q.cmd.invoice`)

### `InvoicePayload`
Carga útil para comandos de generación de boletas o facturas electrónicas en PDF y su posterior despacho por correo.

| Campo | Tipo | Requerido | Descripción | Ejemplo |
|---|---|---|---|---|
| `orderId` | String | Sí | Identificador único del pedido | `"PED-1024"` |
| `invoiceNumber` | String | Sí | Número de folio del comprobante | `"BOL-2026-00451"` |
| `invoiceType` | String | Sí | Tipo de comprobante (`"BOLETA"` o `"FACTURA"`) | `"BOLETA"` |
| `issueDate` | String (ISO-8601) | Sí | Fecha de emisión del comprobante | `"2026-10-01T15:28:00Z"` |
| `customer` | `CustomerInfo` | Sí | Datos del cliente receptor de la compra | Ver detalle abajo |
| `items` | List<`InvoiceItem`>| Sí | Listado de ítems de café y accesorios adquiridos | Mínimo 1 elemento |
| `currency` | String | Sí | Código de moneda (ISO-4217) | `"CLP"` / `"USD"` / `"PEN"` |
| `subtotal` | BigDecimal | Sí | Monto antes de impuestos | `15000.00` |
| `tax` | BigDecimal | Sí | Monto correspondiente a impuestos (IVA) | `2850.00` |
| `total` | BigDecimal | Sí | Monto total liquidado | `17850.00` |

### `CustomerInfo`
Información del cliente para la cabecera de la boleta/factura y envío del correo.

| Campo | Tipo | Requerido | Descripción | Ejemplo |
|---|---|---|---|---|
| `name` | String | Sí | Nombre o razón social | `"Café Gourmet Ltda"` o `"Sofía Morales"` |
| `taxId` | String | Opcional | Identificación tributaria (RUT / RUC / DNI) | `"76.123.456-7"` |
| `email` | String | Sí | Correo electrónico donde se adjuntará el comprobante | `"cliente.cafe@example.com"` |
| `address` | String | Opcional | Dirección fiscal o de entrega | `"Av. Providencia 1234, Santiago"` |

### `InvoiceItem`
Detalle de cada línea de producto dentro del comprobante.

| Campo | Tipo | Requerido | Descripción | Ejemplo |
|---|---|---|---|---|
| `description` | String | Sí | Nombre/detalle del producto de café | `"Café Grano Geisha 250g - Origen Huila"` |
| `quantity` | Integer | Sí | Cantidad comprada (debe ser > 0) | `2` |
| `unitPrice` | BigDecimal | Sí | Precio unitario del ítem | `7500.00` |
| `totalPrice` | BigDecimal | Sí | Subtotal de la línea (`quantity * unitPrice`) | `15000.00` |

---

## 4. Modelos Operacionales y en Memoria

### `IdempotencyEntry`
Estructura en memoria gestionada por `IdempotencyService` para registrar el procesamiento deduplicado por `eventId`.

- `eventId`: String (clave primaria de deduplicación).
- `processedAt`: Instant (marca de tiempo en la que se completó el procesamiento).
- `flowType`: String (`"EMAIL"` o `"INVOICE"`).

### `GeneratedPdfDocument`
Artefacto binario generado en memoria por `InvoicePdfGenerator`.

- `filename`: String (ej. `"Boleta_BOL-2026-00451.pdf"`).
- `contentBytes`: byte[] (array de bytes generado con OpenPDF).
- `contentType`: String (`"application/pdf"`).
- `sizeBytes`: long (tamaño del archivo generado).
