package com.pedidos360.notify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {

    private final RabbitMQConfig rabbitMQConfig = new RabbitMQConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Debe declarar correctamente los exchanges cmd.direct y cmd.dlx")
    void testExchangesDeclaration() {
        DirectExchange directExchange = rabbitMQConfig.cmdDirectExchange();
        DirectExchange dlxExchange = rabbitMQConfig.cmdDlxExchange();

        assertEquals(RabbitMQConfig.EXCHANGE_CMD_DIRECT, directExchange.getName());
        assertTrue(directExchange.isDurable());

        assertEquals(RabbitMQConfig.EXCHANGE_CMD_DLX, dlxExchange.getName());
        assertTrue(dlxExchange.isDurable());
    }

    @Test
    @DisplayName("Debe configurar las colas principales con sus argumentos hacia la DLQ")
    void testQueuesWithDeadLetterExchange() {
        Queue emailQueue = rabbitMQConfig.emailQueue();
        Queue invoiceQueue = rabbitMQConfig.invoiceQueue();

        assertEquals(RabbitMQConfig.QUEUE_CMD_EMAIL, emailQueue.getName());
        assertEquals(RabbitMQConfig.EXCHANGE_CMD_DLX, emailQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitMQConfig.ROUTING_KEY_EMAIL_DLQ, emailQueue.getArguments().get("x-dead-letter-routing-key"));

        assertEquals(RabbitMQConfig.QUEUE_CMD_INVOICE, invoiceQueue.getName());
        assertEquals(RabbitMQConfig.EXCHANGE_CMD_DLX, invoiceQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitMQConfig.ROUTING_KEY_INVOICE_DLQ, invoiceQueue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    @DisplayName("Debe declarar las colas DLQ y sus respectivos bindings")
    void testDlqQueuesAndBindings() {
        Queue emailDlq = rabbitMQConfig.emailDlqQueue();
        Queue invoiceDlq = rabbitMQConfig.invoiceDlqQueue();
        DirectExchange dlx = rabbitMQConfig.cmdDlxExchange();

        assertEquals(RabbitMQConfig.QUEUE_CMD_EMAIL_DLQ, emailDlq.getName());
        assertEquals(RabbitMQConfig.QUEUE_CMD_INVOICE_DLQ, invoiceDlq.getName());

        Binding emailDlqBinding = rabbitMQConfig.emailDlqBinding(emailDlq, dlx);
        assertEquals(RabbitMQConfig.ROUTING_KEY_EMAIL_DLQ, emailDlqBinding.getRoutingKey());

        Binding invoiceDlqBinding = rabbitMQConfig.invoiceDlqBinding(invoiceDlq, dlx);
        assertEquals(RabbitMQConfig.ROUTING_KEY_INVOICE_DLQ, invoiceDlqBinding.getRoutingKey());
    }

    @Test
    @DisplayName("Debe configurar el convertidor de mensajes JSON con módulo de fechas")
    void testMessageConverterConfigured() {
        MessageConverter converter = rabbitMQConfig.messageConverter(objectMapper);
        assertNotNull(converter);
    }
}
