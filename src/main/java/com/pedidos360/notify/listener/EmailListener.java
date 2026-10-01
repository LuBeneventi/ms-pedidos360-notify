package com.pedidos360.notify.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.notify.config.RabbitMQConfig;
import com.pedidos360.notify.domain.envelope.BaseEventEnvelope;
import com.pedidos360.notify.domain.model.EmailPayload;
import com.pedidos360.notify.service.EmailSenderService;
import com.pedidos360.notify.service.IdempotencyService;
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
 * Consumidor para la cola q.cmd.email.
 * Procesa mensajes de notificación por correo de forma idempotente,
 * emitiendo ACK explícito en éxito o NACK (sin requeue) hacia la DLQ tras fallos.
 */
@Component
public class EmailListener {

    private static final Logger log = LoggerFactory.getLogger(EmailListener.class);

    private final EmailSenderService emailSenderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final int maxRetryAttempts;
    private final long retryBackoffMs;

    public EmailListener(
            EmailSenderService emailSenderService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            @Value("${pedidos360.retry.max-attempts:3}") int maxRetryAttempts,
            @Value("${pedidos360.retry.backoff-ms:1000}") long retryBackoffMs) {
        this.emailSenderService = emailSenderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryBackoffMs = retryBackoffMs;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CMD_EMAIL, containerFactory = "rabbitListenerContainerFactory")
    public void handleEmailCommand(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        BaseEventEnvelope<EmailPayload> envelope = null;
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            envelope = objectMapper.readValue(json, new TypeReference<BaseEventEnvelope<EmailPayload>>() {});
        } catch (Exception e) {
            log.error("Mensaje malformado o JSON inválido en {}. Descartando a DLQ...", RabbitMQConfig.QUEUE_CMD_EMAIL, e);
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        if (envelope == null || envelope.getPayload() == null) {
            log.error("Envelope o payload nulo en {}. Descartando a DLQ...", RabbitMQConfig.QUEUE_CMD_EMAIL);
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        // Configurar contexto de logs estructurados MDC
        configureMdc(envelope);

        try {
            String eventId = envelope.getEventId();
            log.info("Mensaje recibido en {}: type={}, eventId={}", RabbitMQConfig.QUEUE_CMD_EMAIL, envelope.getType(), eventId);

            // Verificación de Idempotencia
            if (!idempotencyService.checkAndMarkProcessed(eventId)) {
                log.info("Idempotencia activada: eventId '{}' ya fue procesado anteriormente. Omitiendo reenvío y emitiendo ACK.", eventId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // Validación de campos obligatorios
            EmailPayload payload = envelope.getPayload();
            if (payload.getTo() == null || payload.getTo().isBlank() ||
                payload.getSubject() == null || payload.getSubject().isBlank() ||
                payload.getOrderNumber() == null || payload.getOrderNumber().isBlank()) {
                log.error("Payload incompleto para eventId {}. Rechazando hacia DLQ...", eventId);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // Procesamiento y política de reintentos
            executeWithRetry(payload, channel, deliveryTag, eventId);

        } finally {
            clearMdc();
        }
    }

    private void executeWithRetry(EmailPayload payload, Channel channel, long deliveryTag, String eventId) throws IOException {
        int attempt = 0;
        while (attempt < maxRetryAttempts) {
            attempt++;
            try {
                emailSenderService.sendEmail(payload);
                log.info("Envío exitoso para eventId {}. Confirmando ACK en RabbitMQ...", eventId);
                channel.basicAck(deliveryTag, false);
                return;
            } catch (Exception e) {
                log.warn("Fallo al enviar correo en intento {}/{} para eventId {}: {}", attempt, maxRetryAttempts, eventId, e.getMessage());
                if (attempt >= maxRetryAttempts) {
                    log.error("Reintentos agotados ({}/{}) para eventId {}. Emitiendo NACK sin requeue hacia DLQ...", attempt, maxRetryAttempts, eventId);
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
