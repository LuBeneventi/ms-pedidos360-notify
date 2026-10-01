package com.pedidos360.notify.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pedidos360.notify.domain.envelope.BaseEventEnvelope;
import com.pedidos360.notify.domain.model.EmailPayload;
import com.pedidos360.notify.service.EmailSenderService;
import com.pedidos360.notify.service.IdempotencyService;
import com.rabbitmq.client.Channel;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailListenerTest {

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Channel channel;

    private ObjectMapper objectMapper;
    private EmailListener emailListener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        emailListener = new EmailListener(emailSenderService, idempotencyService, objectMapper, 2, 50);
    }

    private Message createMessage(BaseEventEnvelope<EmailPayload> envelope) throws Exception {
        String json = objectMapper.writeValueAsString(envelope);
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    @DisplayName("Mensaje nuevo y válido procesa envío y confirma con ACK")
    void testValidEmailCommandSuccess() throws Exception {
        EmailPayload payload = new EmailPayload("cliente@cafe.com", "Pedido Confirmado", "PED-1024", "Sofía", "CONFIRMADO", "Detalle", Map.of());
        BaseEventEnvelope<EmailPayload> envelope = new BaseEventEnvelope<>(
                "cmd.email.send", "EVT-100", Instant.now(), "trace-1", "corr-1", payload
        );
        Message message = createMessage(envelope);
        long deliveryTag = 1L;

        when(idempotencyService.checkAndMarkProcessed("EVT-100")).thenReturn(true);

        emailListener.handleEmailCommand(message, channel, deliveryTag);

        verify(emailSenderService, times(1)).sendEmail(any(EmailPayload.class));
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Mensaje con eventId ya procesado se descarta y confirma con ACK (Idempotencia)")
    void testDuplicateEventSkippedWithAck() throws Exception {
        EmailPayload payload = new EmailPayload("cliente@cafe.com", "Pedido Confirmado", "PED-1024", "Sofía", "CONFIRMADO", "Detalle", Map.of());
        BaseEventEnvelope<EmailPayload> envelope = new BaseEventEnvelope<>(
                "cmd.email.send", "EVT-DUPLICATE", Instant.now(), "trace-1", "corr-1", payload
        );
        Message message = createMessage(envelope);
        long deliveryTag = 2L;

        when(idempotencyService.checkAndMarkProcessed("EVT-DUPLICATE")).thenReturn(false);

        emailListener.handleEmailCommand(message, channel, deliveryTag);

        verify(emailSenderService, never()).sendEmail(any());
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Fallo persistente tras agotar reintentos emite NACK sin requeue (hacia DLQ)")
    void testFailureExhaustedSendsNack() throws Exception {
        EmailPayload payload = new EmailPayload("cliente@cafe.com", "Pedido Confirmado", "PED-1024", "Sofía", "CONFIRMADO", "Detalle", Map.of());
        BaseEventEnvelope<EmailPayload> envelope = new BaseEventEnvelope<>(
                "cmd.email.send", "EVT-FAIL", Instant.now(), "trace-1", "corr-1", payload
        );
        Message message = createMessage(envelope);
        long deliveryTag = 3L;

        when(idempotencyService.checkAndMarkProcessed("EVT-FAIL")).thenReturn(true);
        doThrow(new MessagingException("SMTP connection refused")).when(emailSenderService).sendEmail(any(EmailPayload.class));

        emailListener.handleEmailCommand(message, channel, deliveryTag);

        verify(emailSenderService, times(2)).sendEmail(any(EmailPayload.class));
        verify(channel, times(1)).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Mensaje JSON inválido o malformado emite NACK sin requeue inmediato")
    void testMalformedJsonSendsNack() throws IOException {
        Message badMessage = new Message("not a valid json".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        long deliveryTag = 4L;

        emailListener.handleEmailCommand(badMessage, channel, deliveryTag);

        verify(channel, times(1)).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
