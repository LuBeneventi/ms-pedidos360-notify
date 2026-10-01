# Feature Specification: Consumidores de Notificaciones y Comprobantes (ms-pedidos360-notify)

**Feature Branch**: `001-notification-consumers`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Construye dos consumidores del microservicio ms-pedidos360-notify. Flujo 1 - Correos (cola q.cmd.email): Cada mensaje trae un envelope común (type, eventId, timestamp, traceId, correlationId) y un payload con destinatario, asunto y datos para armar el correo (ej. número de pedido, estado nuevo). Al recibir un mensaje válido, el servicio envía el correo y hace ACK. Si falla el envío, reintenta un número limitado de veces; si sigue fallando, hace NACK y el mensaje termina en q.cmd.email.dlq. Flujo 2 - Boletas/Facturas (cola q.cmd.invoice): Cada mensaje trae el mismo envelope común y un payload con los datos del pedido (cliente, items, precios, total). Al recibir un mensaje válido, el servicio genera un PDF de boleta/factura con esos datos y lo envía como adjunto por correo al cliente, reutilizando la configuración SMTP del Flujo 1, y hace ACK. Si la generación del PDF o el envío fallan, reintenta un número limitado de veces; si sigue fallando, hace NACK y el mensaje termina en q.cmd.invoice.dlq. En ambos flujos, si se recibe un eventId ya procesado anteriormente, el servicio lo ignora (idempotencia) y hace ACK sin reprocesar."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Procesamiento y Envío de Notificaciones por Correo (Priority: P1)

Como cliente del negocio de venta de café Pedidos360, deseo recibir correos electrónicos informativos oportunos cuando se produzcan eventos sobre mis pedidos (por ejemplo confirmación, actualización de estado o despacho), para mantenerme al tanto del estado de mi compra.

**Why this priority**: Es el canal directo de comunicación hacia el cliente para confirmar transacciones y cambios de estado en la plataforma de café. Constituye el núcleo funcional del microservicio de notificaciones.

**Independent Test**: Se puede probar de forma aislada publicando un comando en la cola `q.cmd.email` con datos de destinatario, asunto y estado del pedido, verificando la salida exitosa del correo y la emisión del ACK correspondiente.

**Acceptance Scenarios**:

1. **Given** un mensaje válido en `q.cmd.email` con `eventId` no procesado previamente, destinatario válido y contenido del pedido, **When** el consumidor procesa el mensaje, **Then** el correo electrónico es despachado al destinatario y se emite un ACK para confirmar el mensaje en la cola.
2. **Given** un mensaje válido en `q.cmd.email`, **When** ocurre un fallo transitorio de comunicación con el servicio de correo, **Then** el sistema reintenta el envío hasta el límite configurado antes de tomar una acción definitiva.
3. **Given** un mensaje en `q.cmd.email` cuyo envío falla de manera persistente tras agotar todos los reintentos permitidos, **When** se comprueba el fallo definitivo, **Then** el sistema emite un NACK y el mensaje es desviado a `q.cmd.email.dlq` sin pérdida de información.

---

### User Story 2 - Generación y Entrega de Comprobantes de Pago en PDF (Priority: P2)

Como cliente que ha realizado una compra de café, deseo recibir por correo electrónico mi comprobante de pago (boleta o factura) generado en formato PDF con el detalle desglosado de productos, precios y total pagado, para tener constancia tributaria de mi compra.

**Why this priority**: Garantiza el cumplimiento comercial y normativo al emitir comprobantes de venta válidos y entregarlos directamente al comprador como adjunto.

**Independent Test**: Se puede probar de forma aislada publicando un comando en la cola `q.cmd.invoice` con los datos del pedido (cliente, items, importes), verificando que se genera un archivo PDF estructurado y se envía como adjunto al correo del cliente con ACK en la cola.

**Acceptance Scenarios**:

1. **Given** un comando válido en `q.cmd.invoice` con el detalle del pedido (cliente, items, precios y total) y un `eventId` nuevo, **When** el consumidor procesa el mensaje, **Then** se compila el documento PDF correspondiente a la boleta/factura, se adjunta a un correo dirigido al cliente y se confirma el mensaje con un ACK.
2. **Given** un mensaje en `q.cmd.invoice` cuyos datos de facturación presentan inconsistencias irrecuperables o la generación del PDF falla repetidamente tras reintentos, **When** se agotan las opciones de recuperación, **Then** el sistema emite un NACK y transfiere el mensaje a `q.cmd.invoice.dlq`.
3. **Given** un mensaje en `q.cmd.invoice` donde el PDF se generó satisfactoriamente pero el despacho por correo falla tras todos los reintentos, **When** concluye la política de reintento, **Then** se emite un NACK redirigiendo a `q.cmd.invoice.dlq`.

---

### User Story 3 - Garantía de Idempotencia y Deduplicación por eventId (Priority: P3)

Como operador del sistema de pedidos, requiero que el reprocesamiento accidental o la re-entrega de mensajes con un `eventId` previamente atendido no genere correos duplicados ni comprobantes duplicados para los clientes, manteniendo la consistencia del servicio.

**Why this priority**: En sistemas distribuidos y asíncronos con semántica de entrega "al menos una vez" (at-least-once), la idempotencia evita el spam y la confusión del usuario final por notificaciones o cobros duplicados.

**Independent Test**: Se puede probar enviando consecutivamente dos mensajes idénticos (mismo `eventId`) a cualquiera de las colas, comprobando que solo el primero dispara el envío real mientras que el segundo genera ACK inmediato sin re-ejecutar el envío.

**Acceptance Scenarios**:

1. **Given** que un mensaje con `eventId` "EVT-1001" fue procesado exitosamente en `q.cmd.email`, **When** arriba nuevamente a la cola un mensaje con el mismo `eventId` "EVT-1001", **Then** el sistema detecta la duplicación, omite el envío del correo y responde inmediatamente con ACK.
2. **Given** que un mensaje con `eventId` "EVT-2002" fue procesado exitosamente en `q.cmd.invoice`, **When** arriba nuevamente a la cola un mensaje con el mismo `eventId` "EVT-2002", **Then** el sistema omite la generación del PDF y el reenvío por correo, emitiendo ACK de confirmación.

---

### Edge Cases

- **Mensaje con estructura o campos obligatorios ausentes**: Si el mensaje recibido no cumple con el contrato del sobre (`eventId`, `type`, etc.) o el payload carece de destinatario o items, el sistema no debe entrar en un ciclo infinito de reintentos; debe rechazar el mensaje enviándolo a la DLQ correspondiente tras el primer intento fallido.
- **Formato de correo electrónico inválido**: Si la dirección del destinatario tiene un formato sintácticamente incorrecto que cause error inmediato del transporte de correo, debe considerarse fallo no reintentable y canalizarse a la DLQ con registro del motivo.
- **Interrupción de conectividad con el servidor de correo**: Si el servidor SMTP se encuentra temporalmente inalcanzable, las políticas de reintento deben aplicar un espaciado antes de declarar el agotamiento de reintentos.
- **Mensajes concurrentes con idéntico `eventId`**: Si dos mensajes con el mismo `eventId` llegan simultáneamente, el mecanismo de deduplicación debe serializar el control de unicidad para evitar que ambos procesen la acción en paralelo.
- **Pedido con total cero o lista de ítems vacía**: En el flujo de facturación, si el comando contiene un pedido sin ítems o con importes negativos o inconsistentes, debe rechazarse hacia la DLQ evitando emitir comprobantes inválidos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE consumir mensajes asíncronos desde la cola `q.cmd.email` conformes a la estructura de envelope estándar (`type`, `eventId`, `timestamp`, `traceId`, `correlationId`) y su payload asociado.
- **FR-002**: El sistema DEBE validar la presencia y completitud de los campos obligatorios del envelope y del payload en los mensajes de correo (destinatario, asunto, datos de pedido).
- **FR-003**: El sistema DEBE despachar el correo electrónico a través del servicio de correo configurado y emitir una confirmación positiva (ACK) al intermediario de mensajería una vez concretado el envío.
- **FR-004**: El sistema DEBE reintentar de forma controlada el envío de correos ante errores transitorios hasta un umbral configurable de reintentos.
- **FR-005**: El sistema DEBE emitir un rechazo explícito (NACK/Reject) enviando el mensaje a la cola de mensajes muertos `q.cmd.email.dlq` si el procesamiento falla de forma irrecuperable o supera el límite de reintentos.
- **FR-006**: El sistema DEBE consumir mensajes asíncronos desde la cola `q.cmd.invoice` que contengan el envelope estándar y los datos del pedido (datos de cliente, listado de ítems, cantidades, precios unitarios y monto total).
- **FR-007**: El sistema DEBE generar un documento en formato PDF con la representación gráfica y desglosada de la boleta o factura de venta de café a partir de la información del pedido.
- **FR-008**: El sistema DEBE enviar el documento PDF generado como archivo adjunto en un correo electrónico dirigido al cliente, empleando la misma configuración del servicio de correo del flujo de notificaciones.
- **FR-009**: El sistema DEBE emitir una confirmación positiva (ACK) en `q.cmd.invoice` únicamente tras la generación exitosa del PDF y la entrega confirmada al transporte de correo.
- **FR-010**: El sistema DEBE reintentar de manera controlada los fallos transitorios en la generación o despacho del comprobante, y emitir NACK enrutando hacia `q.cmd.invoice.dlq` al agotarse los reintentos o ante errores irrecuperables de validación.
- **FR-011**: El sistema DEBE verificar si el identificador único `eventId` del mensaje ya fue procesado con anterioridad tanto en `q.cmd.email` como en `q.cmd.invoice`.
- **FR-012**: El sistema DEBE descartar la ejecución repetida del envío de correos o la creación de comprobantes si el `eventId` ya fue registrado como procesado, confirmando inmediatamente la recepción con un ACK.
- **FR-013**: El sistema DEBE registrar en las trazas operacionales el `eventId`, `traceId` y `correlationId` para cada mensaje procesado, reintentado, confirmado o enviado a la DLQ.

### Key Entities *(include if feature involves data)*

- **Message Envelope**: Estructura de cabecera común para todos los mensajes del sistema. Contiene `type` (tipo de comando/evento), `eventId` (identificador único global UUID), `timestamp` (marca de tiempo ISO-8601 del origen), `traceId` (identificador para trazabilidad distribuida) y `correlationId` (identificador de la saga o flujo de negocio).
- **Email Notification Command**: Carga útil para la cola `q.cmd.email`. Contiene la dirección del destinatario, asunto del mensaje, plantilla o tipo de notificación y parámetros contextuales del pedido (código de orden, estado, nombre del cliente).
- **Invoice Command**: Carga útil para la cola `q.cmd.invoice`. Contiene la información de facturación del cliente (nombre, identificación fiscal/DNI/RUT, correo electrónico), detalle de ítems de café (descripción, cantidad, precio unitario, subtotales), descuentos e importe total a pagar.
- **Invoice PDF Document**: Documento binario generado dinámicamente que consolida la representación formal de la boleta/factura con membrete del negocio Pedidos360, numeración, desglose de ítems e importes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los mensajes válidos recibidos en `q.cmd.email` procesados sin incidencias resultan en un correo electrónico emitido y su correspondiente ACK en menos de 5 segundos bajo condiciones estándar de red.
- **SC-002**: El 100% de los mensajes válidos en `q.cmd.invoice` generan un PDF legible y se entregan adjuntos al cliente con ACK confirmado en menos de 8 segundos por mensaje.
- **SC-003**: 0% de correos duplicados o comprobantes PDF duplicados emitidos cuando se re-entregan mensajes con el mismo `eventId`.
- **SC-004**: El 100% de los mensajes con errores no recuperables o que sobrepasan el número máximo de reintentos son transferidos a su respectiva cola de descarte (`q.cmd.email.dlq` o `q.cmd.invoice.dlq`) sin detención ni caída del servicio consumidor.
- **SC-005**: El 100% de los eventos procesados o derivados a DLQ cuentan con trazabilidad identificable mediante `traceId` y `eventId` en los registros del sistema.

## Assumptions

- Los sistemas emisores de pedidos garantizan la generación de un identificador `eventId` único y no nulo para cada mensaje emitido.
- La infraestructura de mensajería (RabbitMQ) provee las colas principales (`q.cmd.email`, `q.cmd.invoice`) y sus colas DLQ vinculadas (`q.cmd.email.dlq`, `q.cmd.invoice.dlq`) configuradas con políticas de enrutamiento Dead Letter Exchange.
- El microservicio no cuenta con base de datos relacional propia (conforme a la Constitución del proyecto), por lo que la deduplicación de `eventId` se gestiona mediante un mecanismo ligero/en memoria con ventana de tiempo de retención configurable para mitigar re-entregas comunes.
- Las credenciales y parámetros de conexión hacia el servidor de correo electrónico (host SMTP, puerto, usuario, contraseña/token, remitente) son suministrados mediante variables de entorno en el entorno de despliegue.
- El contenido del PDF de boleta/factura se genera a partir de plantillas en memoria sin necesidad de almacenamiento externo persistente obligatorio antes del despacho por correo.
