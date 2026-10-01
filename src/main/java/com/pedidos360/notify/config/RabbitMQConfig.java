package com.pedidos360.notify.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de topología RabbitMQ:
 * - Exchanges: cmd.direct, cmd.dlx
 * - Colas principales con configuración x-dead-letter-exchange hacia cmd.dlx:
 *   q.cmd.email y q.cmd.invoice
 * - Colas de descarte DLQ: q.cmd.email.dlq y q.cmd.invoice.dlq
 * - Serialización JSON mediante Jackson
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_CMD_DIRECT = "cmd.direct";
    public static final String EXCHANGE_CMD_DLX = "cmd.dlx";

    public static final String QUEUE_CMD_EMAIL = "q.cmd.email";
    public static final String QUEUE_CMD_INVOICE = "q.cmd.invoice";

    public static final String QUEUE_CMD_EMAIL_DLQ = "q.cmd.email.dlq";
    public static final String QUEUE_CMD_INVOICE_DLQ = "q.cmd.invoice.dlq";

    public static final String ROUTING_KEY_EMAIL = "cmd.email";
    public static final String ROUTING_KEY_INVOICE = "cmd.invoice";

    public static final String ROUTING_KEY_EMAIL_DLQ = "cmd.email.dlq";
    public static final String ROUTING_KEY_INVOICE_DLQ = "cmd.invoice.dlq";

    @Bean
    public DirectExchange cmdDirectExchange() {
        return new DirectExchange(EXCHANGE_CMD_DIRECT, true, false);
    }

    @Bean
    public DirectExchange cmdDlxExchange() {
        return new DirectExchange(EXCHANGE_CMD_DLX, true, false);
    }

    @Bean
    public Queue emailDlqQueue() {
        return QueueBuilder.durable(QUEUE_CMD_EMAIL_DLQ).build();
    }

    @Bean
    public Queue invoiceDlqQueue() {
        return QueueBuilder.durable(QUEUE_CMD_INVOICE_DLQ).build();
    }

    @Bean
    public Binding emailDlqBinding(Queue emailDlqQueue, DirectExchange cmdDlxExchange) {
        return BindingBuilder.bind(emailDlqQueue).to(cmdDlxExchange).with(ROUTING_KEY_EMAIL_DLQ);
    }

    @Bean
    public Binding invoiceDlqBinding(Queue invoiceDlqQueue, DirectExchange cmdDlxExchange) {
        return BindingBuilder.bind(invoiceDlqQueue).to(cmdDlxExchange).with(ROUTING_KEY_INVOICE_DLQ);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(QUEUE_CMD_EMAIL)
                .withArgument("x-dead-letter-exchange", EXCHANGE_CMD_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_EMAIL_DLQ)
                .build();
    }

    @Bean
    public Queue invoiceQueue() {
        return QueueBuilder.durable(QUEUE_CMD_INVOICE)
                .withArgument("x-dead-letter-exchange", EXCHANGE_CMD_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_INVOICE_DLQ)
                .build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, DirectExchange cmdDirectExchange) {
        return BindingBuilder.bind(emailQueue).to(cmdDirectExchange).with(ROUTING_KEY_EMAIL);
    }

    @Bean
    public Binding invoiceBinding(Queue invoiceQueue, DirectExchange cmdDirectExchange) {
        return BindingBuilder.bind(invoiceQueue).to(cmdDirectExchange).with(ROUTING_KEY_INVOICE);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper.copy();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        return factory;
    }
}
