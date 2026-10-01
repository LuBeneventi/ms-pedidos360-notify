package com.pedidos360.notify.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pedidos360.notify.domain.envelope.BaseEventEnvelope;
import com.pedidos360.notify.domain.model.CustomerInfo;
import com.pedidos360.notify.domain.model.EmailPayload;
import com.pedidos360.notify.domain.model.GeneratedPdfDocument;
import com.pedidos360.notify.domain.model.InvoiceItem;
import com.pedidos360.notify.domain.model.InvoicePayload;
import com.pedidos360.notify.service.EmailSenderService;
import com.pedidos360.notify.service.IdempotencyService;
import com.pedidos360.notify.service.InvoicePdfGenerator;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyFlowTest {

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private InvoicePdfGenerator invoicePdfGenerator;

    @Mock
    private Channel emailChannel;

    @Mock
    private Channel invoiceChannel;

    private IdempotencyService sharedIdempotencyService;
    private ObjectMapper objectMapper;
    private EmailListener emailListener;
    private InvoiceListener invoiceListener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        sharedIdempotencyService = new IdempotencyService(24, 1000);

        emailListener = new EmailListener(emailSenderService, sharedIdempotencyService, objectMapper, 2, 50);
        invoiceListener = new InvoiceListener(invoicePdfGenerator, emailSenderService, sharedIdempotencyService, objectMapper, 2, 50);
    }

    private <T> Message createMessage(BaseEventEnvelope<T> envelope) throws Exception {
        String json = objectMapper.writeValueAsString(envelope);
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    @DisplayName("Flujo de Correo: El segundo mensaje con el mismo eventId se descarta sin enviar correo pero con ACK")
    void testEmailDeduplicationFlow() throws Exception {
        String eventId = "EMAIL-EVT-777";
        EmailPayload payload = new EmailPayload("cliente@cafe.com", "Tu café está listo", "PED-777", "Juan", "LISTO", "Cuerpo", Map.of());
        BaseEventEnvelope<EmailPayload> envelope = new BaseEventEnvelope<>(
                "cmd.email.send", eventId, Instant.now(), "trace-777", "corr-777", payload
        );
        Message message = createMessage(envelope);

        // Primer envío: Debe procesar y emitir ACK
        emailListener.handleEmailCommand(message, emailChannel, 1L);
        verify(emailSenderService, times(1)).sendEmail(any(EmailPayload.class));
        verify(emailChannel, times(1)).basicAck(1L, false);

        // Segundo envío idéntico: Debe omitir el envío y emitir ACK inmediatamente
        emailListener.handleEmailCommand(message, emailChannel, 2L);
        verify(emailSenderService, times(1)).sendEmail(any(EmailPayload.class)); // Sigue siendo 1
        verify(emailChannel, times(1)).basicAck(2L, false);
    }

    @Test
    @DisplayName("Flujo de Boleta/Factura: El segundo mensaje con el mismo eventId se descarta sin generar PDF pero con ACK")
    void testInvoiceDeduplicationFlow() throws Exception {
        String eventId = "INVOICE-EVT-888";
        CustomerInfo customer = new CustomerInfo("Ana Silva", "12.345.678-9", "ana@cliente.com", "Calle Café 123");
        List<InvoiceItem> items = List.of(new InvoiceItem("Café Latte Pack", 1, new BigDecimal("8500"), new BigDecimal("8500")));
        InvoicePayload payload = new InvoicePayload(
                "PED-888", "BOL-888", "BOLETA", Instant.now(), customer, items, "CLP",
                new BigDecimal("7143"), new BigDecimal("1357"), new BigDecimal("8500")
        );
        BaseEventEnvelope<InvoicePayload> envelope = new BaseEventEnvelope<>(
                "cmd.invoice.generate", eventId, Instant.now(), "trace-888", "corr-888", payload
        );
        Message message = createMessage(envelope);

        GeneratedPdfDocument mockPdf = new GeneratedPdfDocument("boleta_BOL-888.pdf", "%PDF-sample".getBytes());
        when(invoicePdfGenerator.generateInvoicePdf(any(InvoicePayload.class))).thenReturn(mockPdf);

        // Primer procesamiento
        invoiceListener.handleInvoiceCommand(message, invoiceChannel, 10L);
        verify(invoicePdfGenerator, times(1)).generateInvoicePdf(any(InvoicePayload.class));
        verify(invoiceChannel, times(1)).basicAck(10L, false);

        // Segundo procesamiento idéntico
        invoiceListener.handleInvoiceCommand(message, invoiceChannel, 11L);
        verify(invoicePdfGenerator, times(1)).generateInvoicePdf(any(InvoicePayload.class)); // Sigue siendo 1
        verify(invoiceChannel, times(1)).basicAck(11L, false);
    }
}
