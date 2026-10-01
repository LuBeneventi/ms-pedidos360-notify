package com.pedidos360.notify.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pedidos360.notify.domain.envelope.BaseEventEnvelope;
import com.pedidos360.notify.domain.model.CustomerInfo;
import com.pedidos360.notify.domain.model.GeneratedPdfDocument;
import com.pedidos360.notify.domain.model.InvoiceItem;
import com.pedidos360.notify.domain.model.InvoicePayload;
import com.pedidos360.notify.service.EmailSenderService;
import com.pedidos360.notify.service.IdempotencyService;
import com.pedidos360.notify.service.InvoicePdfGenerator;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceListenerTest {

    @Mock
    private InvoicePdfGenerator invoicePdfGenerator;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Channel channel;

    private ObjectMapper objectMapper;
    private InvoiceListener invoiceListener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        invoiceListener = new InvoiceListener(invoicePdfGenerator, emailSenderService, idempotencyService, objectMapper, 2, 50);
    }

    private Message createMessage(BaseEventEnvelope<InvoicePayload> envelope) throws Exception {
        String json = objectMapper.writeValueAsString(envelope);
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private InvoicePayload createSampleInvoice() {
        CustomerInfo customer = new CustomerInfo("Carlos Ruiz", "15.432.109-8", "carlos@cliente.com", "Los Aromos 45");
        List<InvoiceItem> items = List.of(
                new InvoiceItem("Café Espresso Blend 1kg", 1, new BigDecimal("22000"), new BigDecimal("22000"))
        );
        return new InvoicePayload(
                "PED-5050",
                "BOL-2026-00999",
                "BOLETA",
                Instant.now(),
                customer,
                items,
                "CLP",
                new BigDecimal("18487"),
                new BigDecimal("3513"),
                new BigDecimal("22000")
        );
    }

    @Test
    @DisplayName("Comando de factura válido genera PDF, despacha correo con adjunto y emite ACK")
    void testValidInvoiceCommandSuccess() throws Exception {
        InvoicePayload payload = createSampleInvoice();
        BaseEventEnvelope<InvoicePayload> envelope = new BaseEventEnvelope<>(
                "cmd.invoice.generate", "EVT-INV-1", Instant.now(), "trace-2", "corr-2", payload
        );
        Message message = createMessage(envelope);
        long deliveryTag = 10L;

        GeneratedPdfDocument mockPdf = new GeneratedPdfDocument("boleta_BOL-2026-00999.pdf", "%PDF-mock-bytes".getBytes());
        when(idempotencyService.checkAndMarkProcessed("EVT-INV-1")).thenReturn(true);
        when(invoicePdfGenerator.generateInvoicePdf(any(InvoicePayload.class))).thenReturn(mockPdf);

        invoiceListener.handleInvoiceCommand(message, channel, deliveryTag);

        verify(invoicePdfGenerator, times(1)).generateInvoicePdf(any(InvoicePayload.class));
        verify(emailSenderService, times(1)).sendEmailWithAttachment(
                eq("carlos@cliente.com"),
                contains("BOL-2026-00999"),
                anyString(),
                eq(mockPdf.getContentBytes()),
                eq(mockPdf.getFilename()),
                eq("application/pdf")
        );
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Mensaje con eventId ya procesado en invoice se descarta emitiendo ACK (Idempotencia)")
    void testDuplicateInvoiceEventSkippedWithAck() throws Exception {
        InvoicePayload payload = createSampleInvoice();
        BaseEventEnvelope<InvoicePayload> envelope = new BaseEventEnvelope<>(
                "cmd.invoice.generate", "EVT-INV-DUPLICATE", Instant.now(), "trace-2", "corr-2", payload
        );
        Message message = createMessage(envelope);
        long deliveryTag = 11L;

        when(idempotencyService.checkAndMarkProcessed("EVT-INV-DUPLICATE")).thenReturn(false);

        invoiceListener.handleInvoiceCommand(message, channel, deliveryTag);

        verify(invoicePdfGenerator, never()).generateInvoicePdf(any());
        verify(emailSenderService, never()).sendEmailWithAttachment(any(), any(), any(), any(), any(), any());
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Fallo persistente en generación de PDF o envío de adjunto emite NACK hacia DLQ")
    void testFailureExhaustedSendsNackToDlq() throws Exception {
        InvoicePayload payload = createSampleInvoice();
        BaseEventEnvelope<InvoicePayload> envelope = new BaseEventEnvelope<>(
                "cmd.invoice.generate", "EVT-INV-FAIL", Instant.now(), "trace-2", "corr-2", payload
        );
        Message message = createMessage(envelope);
        long deliveryTag = 12L;

        when(idempotencyService.checkAndMarkProcessed("EVT-INV-FAIL")).thenReturn(true);
        when(invoicePdfGenerator.generateInvoicePdf(any(InvoicePayload.class)))
                .thenThrow(new RuntimeException("Error simulado de generación de PDF"));

        invoiceListener.handleInvoiceCommand(message, channel, deliveryTag);

        verify(invoicePdfGenerator, times(2)).generateInvoicePdf(any(InvoicePayload.class));
        verify(channel, times(1)).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Mensaje malformado en cola invoice emite NACK sin requeue inmediato")
    void testMalformedInvoiceMessageSendsNack() throws IOException {
        Message badMessage = new Message("invalid json".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        long deliveryTag = 13L;

        invoiceListener.handleInvoiceCommand(badMessage, channel, deliveryTag);

        verify(channel, times(1)).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
