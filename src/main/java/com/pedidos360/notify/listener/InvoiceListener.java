package com.pedidos360.notify.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.notify.config.RabbitMQConfig;
import com.pedidos360.notify.domain.envelope.BaseEventEnvelope;
import com.pedidos360.notify.domain.model.GeneratedPdfDocument;
import com.pedidos360.notify.domain.model.InvoicePayload;
import com.pedidos360.notify.service.EmailSenderService;
import com.pedidos360.notify.service.IdempotencyService;
import com.pedidos360.notify.service.InvoicePdfGenerator;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Consumidor para la cola q.cmd.invoice.
 * Procesa mensajes de generación de comprobantes tributarios (Boleta/Factura) en PDF
 * y los despacha adjuntos por correo al cliente de forma idempotente,
 * emitiendo ACK en éxito o NACK sin requeue hacia q.cmd.invoice.dlq ante fallos.
 */
@Component
public class InvoiceListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceListener.class);

    private final InvoicePdfGenerator invoicePdfGenerator;
    private final EmailSenderService emailSenderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final int maxRetryAttempts;
    private final long retryBackoffMs;

    public InvoiceListener(
            InvoicePdfGenerator invoicePdfGenerator,
            EmailSenderService emailSenderService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            @Value("${pedidos360.retry.max-attempts:3}") int maxRetryAttempts,
            @Value("${pedidos360.retry.backoff-ms:1000}") long retryBackoffMs) {
        this.invoicePdfGenerator = invoicePdfGenerator;
        this.emailSenderService = emailSenderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryBackoffMs = retryBackoffMs;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CMD_INVOICE, containerFactory = "rabbitListenerContainerFactory")
    public void handleInvoiceCommand(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        BaseEventEnvelope<InvoicePayload> envelope = null;
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            envelope = objectMapper.readValue(json, new TypeReference<BaseEventEnvelope<InvoicePayload>>() {});
        } catch (Exception e) {
            log.error("Mensaje malformado o JSON inválido en {}. Descartando hacia DLQ...", RabbitMQConfig.QUEUE_CMD_INVOICE, e);
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        if (envelope == null || envelope.getPayload() == null) {
            log.error("Envelope o payload nulo en {}. Descartando hacia DLQ...", RabbitMQConfig.QUEUE_CMD_INVOICE);
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        configureMdc(envelope);

        try {
            String eventId = envelope.getEventId();
            log.info("Mensaje recibido en {}: type={}, eventId={}", RabbitMQConfig.QUEUE_CMD_INVOICE, envelope.getType(), eventId);

            // Verificación de idempotencia
            if (!idempotencyService.checkAndMarkProcessed(eventId)) {
                log.info("Idempotencia activada: eventId '{}' ya fue procesado anteriormente. Omitiendo generación de PDF y emitiendo ACK.", eventId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            InvoicePayload payload = envelope.getPayload();
            // Validación de campos críticos
            if (payload.getInvoiceNumber() == null || payload.getInvoiceNumber().isBlank() ||
                payload.getCustomer() == null || payload.getCustomer().getEmail() == null || payload.getCustomer().getEmail().isBlank() ||
                payload.getItems() == null || payload.getItems().isEmpty()) {
                log.error("Payload incompleto o inválido para invoice eventId {}. Rechazando hacia DLQ...", eventId);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // Procesamiento de generación y envío con política de reintentos
            executeWithRetry(payload, channel, deliveryTag, eventId);

        } finally {
            clearMdc();
        }
    }

    private void executeWithRetry(InvoicePayload payload, Channel channel, long deliveryTag, String eventId) throws IOException {
        int attempt = 0;
        while (attempt < maxRetryAttempts) {
            attempt++;
            try {
                // 1. Compilación del PDF en memoria
                GeneratedPdfDocument pdfDoc = invoicePdfGenerator.generateInvoicePdf(payload);

                // 2. Armado del correo y envío con el PDF adjunto
                String to = payload.getCustomer().getEmail();
                String subject = "Tu " + (payload.getInvoiceType() != null ? payload.getInvoiceType() : "Comprobante") +
                                 " Electrónica N° " + payload.getInvoiceNumber() + " - Pedidos360";

                String htmlBody = buildInvoiceEmailHtml(payload);

                emailSenderService.sendEmailWithAttachment(
                        to,
                        subject,
                        htmlBody,
                        pdfDoc.getContentBytes(),
                        pdfDoc.getFilename(),
                        pdfDoc.getContentType()
                );

                log.info("Generación de PDF y despacho exitoso para comprobante {} (eventId {}). Confirmando ACK...",
                        payload.getInvoiceNumber(), eventId);
                channel.basicAck(deliveryTag, false);
                return;

            } catch (Exception e) {
                log.warn("Fallo al generar o enviar comprobante en intento {}/{} para eventId {}: {}",
                        attempt, maxRetryAttempts, eventId, e.getMessage());

                if (attempt >= maxRetryAttempts) {
                    log.error("Reintentos agotados ({}/{}) para comprobante eventId {}. Emitiendo NACK sin requeue hacia DLQ...",
                            attempt, maxRetryAttempts, eventId);
                    channel.basicNack(deliveryTag, false, false);
                    return;
                }

                try {
                    Thread.sleep(retryBackoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    channel.basicNack(deliveryTag, false, false);
                    return;
                }
            }
        }
    }

    private String buildInvoiceEmailHtml(InvoicePayload invoice) {
        String clientName = invoice.getCustomer() != null && invoice.getCustomer().getName() != null
                ? invoice.getCustomer().getName()
                : "Estimado/a cliente";

        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: auto; border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden;">
                        <div style="background-color: #6F4E37; color: #ffffff; padding: 20px; text-align: center;">
                            <h1 style="margin: 0; font-size: 22px;">☕ Pedidos360 - Comprobante de Venta</h1>
                        </div>
                        <div style="padding: 24px;">
                            <p style="font-size: 16px;">¡Hola <strong>%s</strong>!</p>
                            <p>Adjunto a este correo encontrarás tu comprobante tributario en formato PDF correspondiente al pedido <strong>%s</strong>.</p>
                            <div style="background-color: #f9f6f0; border-left: 4px solid #6F4E37; padding: 12px; margin: 20px 0;">
                                <p style="margin: 0;"><strong>Folio:</strong> %s</p>
                                <p style="margin: 0;"><strong>Total Pagado:</strong> $ %s %s</p>
                            </div>
                            <p style="font-size: 14px; color: #777;">Agradecemos tu preferencia por nuestro café de especialidad.</p>
                        </div>
                        <div style="background-color: #f4f4f4; color: #888; padding: 12px; text-align: center; font-size: 12px;">
                            Documento tributario digital. Emisión autorizada.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                clientName,
                invoice.getOrderId(),
                invoice.getInvoiceNumber(),
                invoice.getTotal() != null ? invoice.getTotal().toString() : "0",
                invoice.getCurrency() != null ? invoice.getCurrency() : "CLP"
        );
    }

    private void configureMdc(BaseEventEnvelope<?> envelope) {
        if (envelope.getTraceId() != null) {
            MDC.put("traceId", envelope.getTraceId());
        }
        if (envelope.getCorrelationId() != null) {
            MDC.put("correlationId", envelope.getCorrelationId());
        }
        if (envelope.getEventId() != null) {
            MDC.put("eventId", envelope.getEventId());
        }
    }

    private void clearMdc() {
        MDC.remove("traceId");
        MDC.remove("correlationId");
        MDC.remove("eventId");
    }
}
