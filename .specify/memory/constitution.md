<!--
Sync Impact Report:
- Version change: Initial Template -> 1.0.0
- Modified principles:
  - PRINCIPLE_1: I. Plataforma Moderna: Spring Boot 3+ y Java 21
  - PRINCIPLE_2: II. Naturaleza Asíncrona: Consumidor RabbitMQ sin API REST de Negocio
  - PRINCIPLE_3: III. Arquitectura Stateless: Sin Base de Datos Propia
  - PRINCIPLE_4: IV. Procesamiento Idempotente por eventId
  - PRINCIPLE_5: V. ACK/NACK Explícitos y Dead Letter Queue (DLQ)
  - Added PRINCIPLE_6: VI. Configuración SMTP Externalizada y Cero Credenciales Hardcodeadas
  - Added PRINCIPLE_7: VII. Generación Desacoplada de Boletas y Facturas en PDF
  - Added PRINCIPLE_8: VIII. Higiene de Repositorio Java/Maven y Exclusiones Estrictas
- Added sections:
  - Restricciones Arquitectónicas y Operacionales
  - Estándares de Desarrollo y Verificación
  - Governance
- Removed sections: N/A
- Follow-up TODOs: Ninguno. Todos los parámetros y reglas fueron definidos explícitamente.
-->

# Pedidos360 Notificaciones - Constitución del Proyecto

El microservicio de Notificaciones de Pedidos360 es un componente especializado del ecosistema de venta de café, responsable exclusivo del procesamiento de envío de correos electrónicos y la generación de documentos tributarios (boletas/facturas) en formato PDF.

## Core Principles

### I. Plataforma Moderna: Spring Boot 3+ y Java 21
El microservicio DEBE construirse sobre Java 21 LTS y Spring Boot 3.x (o superior), aprovechando las características del lenguaje contemporáneo y el ecosistema reactivo/concurrente optimizado. No se permiten versiones anteriores de Java ni de Spring Framework.

### II. Naturaleza Asíncrona: Consumidor RabbitMQ sin API REST de Negocio
El servicio opera estrictamente como un consumidor asíncrono conducido por eventos y comandos en RabbitMQ.
- NO DEBE exponer endpoints HTTP / API REST con lógica de negocio.
- Únicamente se permite la exposición de endpoints operacionales y de observabilidad (Spring Boot Actuator: `health`, `info`, `metrics`) para orquestación e infraestructura si es requerido.

### III. Arquitectura Stateless: Sin Base de Datos Propia
El microservicio NO DEBE poseer ni conectarse a una base de datos relacional o NoSQL propia para persistencia persistente de estado de negocio. Todo cómputo o transformación se ejecuta a partir de la carga útil del mensaje recibido y sus dependencias de servicio efímeras.

### IV. Procesamiento Idempotente por eventId
El microservicio consume mensajes provenientes de las colas `q.cmd.email` y `q.cmd.invoice` garantizando idempotencia estricta:
- Un mismo `eventId` NO DEBE generar duplicación en el envío de correos electrónicos ni en la generación o despacho del PDF de boletas/facturas ante escenarios de reintento o re-entrega en la mensajería.
- Las estrategias de deduplicación efímera o verificación de despacho deben integrarse respetando la ausencia de almacenamiento relacional acoplado.

### V. Manejo Confiable de Mensajería: ACK/NACK Explícitos y DLQ
La confirmación de recepción y procesamiento en RabbitMQ DEBE ser explícita y determinista:
- Los mensajes procesados exitosamente deben confirmarse mediante ACK explícito.
- Los fallos recuperables se gestionan con reintentos controlados (backoff policy).
- Tras agotar los reintentos o ante fallos irrecuperables (venomous messages), se emite NACK/REJECT y el mensaje DEBE ser enrutado hacia su correspondiente Dead Letter Queue (DLQ) (`q.cmd.email.dlq`, `q.cmd.invoice.dlq`) sin pérdida silenciosa de datos.

### VI. Configuración SMTP Externalizada y Cero Credenciales Hardcodeadas
La integración con el servicio de correo electrónico (SMTP/proveedor transaccional) DEBE configurarse exclusivamente mediante variables de entorno o gestores de configuración externa:
- Queda terminantemente PROHIBIDO hardcodear credenciales, tokens, host o puertos en el código fuente, archivos de propiedades versionados o artefactos de despliegue.

### VII. Generación Desacoplada de Boletas y Facturas en PDF
La recepción de comandos en `q.cmd.invoice` produce un documento PDF tributario (boleta o factura electrónica para clientes de café):
- El mecanismo de entrega (e.g. adjuntarlo en un correo de confirmación, almacenarlo en un bucket S3/Blob Storage temporal, o publicar un evento de finalización) DEBE ser especificado formalmente en la especificación funcional de la característica (`spec.md`) sin acoplar la librería generadora de PDF a una única vía de transporte.

### VIII. Higiene de Repositorio Java/Maven y Exclusiones Estrictas
El repositorio de código fuente DEBE contener única y exclusivamente los artefactos requeridos para construir y ejecutar el microservicio con Apache Maven y Java:
- El archivo `.gitignore` DEBE excluir rigurosamente directorios de compilación (`target/`), configuraciones de IDE (`.idea/`, `*.iml`, `.vscode/`, `.settings/`), archivos temporales y cualquier archivo contenedor de secretos o variables locales (`.env`, certificados).

## Restricciones Arquitectónicas y Operacionales

1. **Gestión de Errores y Logs Estructurados**:
   Todos los registros de log deben emitirse en formato estructurado (o alineado a observabilidad estándar), correlacionando siempre `eventId` y `traceId` en cada operación de consumo, generación o envío.
2. **Resiliencia Operativa**:
   El fallo transitorio en el servicio SMTP externo o en la generación de PDFs no debe tumbar el contenedor; debe activar los circuitos de reintento y políticas de descarte seguro hacia DLQ.

## Estándares de Desarrollo y Verificación

1. **Pruebas Automatizadas**:
   - Pruebas unitarias para formateadores de plantillas, generadores de PDF y lógica de desempaquetado de mensajes.
   - Pruebas de integración con Testcontainers para RabbitMQ garantizando los flujos de ACK, NACK, encolamiento en DLQ e idempotencia de `eventId`.
2. **Quality Gates**:
   Toda compilación debe pasar `mvn clean verify` sin errores y cumplir con las directivas de seguridad estáticas.

## Governance

- Esta constitución representa la máxima autoridad arquitectónica y de diseño para el microservicio de Notificaciones de Pedidos360.
- Cualquier adición de componentes, dependencias o cambios en el contrato de mensajería debe contrastarse con estos principios.
- Las enmiendas a este documento requieren justificación técnica documentada, revisión de impacto y aprobación formal, actualizando el registro de versión semántica.

**Version**: 1.0.0 | **Ratified**: 2026-10-01 | **Last Amended**: 2026-10-01
