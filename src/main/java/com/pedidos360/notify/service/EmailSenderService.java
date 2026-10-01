package com.pedidos360.notify.service;

import com.pedidos360.notify.domain.model.EmailPayload;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Servicio encargado de la composición y envío de correos electrónicos transaccionales
 * utilizando JavaMailSender y MimeMessageHelper.
 */
@Service
public class EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String senderName;

    public EmailSenderService(
            JavaMailSender mailSender,
            @Value("${pedidos360.mail.from:notificaciones@pedidos360.com}") String fromEmail,
            @Value("${pedidos360.mail.sender-name:Pedidos360 Café}") String senderName) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.senderName = senderName;
    }

    /**
     * Envía un correo electrónico informativo a partir de la carga útil del comando de correo.
     *
     * @param payload Datos del mensaje y destinatario
     * @throws MessagingException           Si ocurre un error en el armado del mensaje MIME
     * @throws UnsupportedEncodingException Si la codificación del remitente no es válida
     */
    public void sendEmail(EmailPayload payload) throws MessagingException, UnsupportedEncodingException {
        if (payload == null) {
            throw new IllegalArgumentException("EmailPayload no puede ser nulo");
        }
        if (payload.getTo() == null || payload.getTo().isBlank()) {
            throw new IllegalArgumentException("El destinatario 'to' es obligatorio");
        }

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());

        helper.setFrom(fromEmail, senderName);
        helper.setTo(payload.getTo());
        helper.setSubject(payload.getSubject() != null ? payload.getSubject() : "Notificación de Pedidos360");

        String htmlContent = buildEmailHtml(payload);
        helper.setText(htmlContent, true);

        log.info("Despachando correo a: {} | Asunto: {} | Pedido: {}", payload.getTo(), payload.getSubject(), payload.getOrderNumber());
        mailSender.send(mimeMessage);
        log.info("Correo despachado con éxito a: {}", payload.getTo());
    }

    /**
     * Envía un correo electrónico con un documento adjunto en memoria (ej. PDF de Boleta o Factura).
     *
     * @param to                 Destinatario
     * @param subject            Asunto del mensaje
     * @param htmlBody           Contenido HTML del correo
     * @param attachmentBytes    Bytes del archivo generado
     * @param attachmentFilename Nombre del archivo adjunto
     * @param contentType        Tipo MIME del adjunto (e.g. application/pdf)
     */
    public void sendEmailWithAttachment(
            String to,
            String subject,
            String htmlBody,
            byte[] attachmentBytes,
            String attachmentFilename,
            String contentType) throws MessagingException, UnsupportedEncodingException {

        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("El destinatario 'to' es obligatorio");
        }
        if (attachmentBytes == null || attachmentBytes.length == 0) {
            throw new IllegalArgumentException("Los bytes del adjunto no pueden estar vacíos");
        }

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

        helper.setFrom(fromEmail, senderName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        ByteArrayResource attachmentResource = new ByteArrayResource(attachmentBytes);
        helper.addAttachment(attachmentFilename, attachmentResource, contentType);

        log.info("Despachando correo con adjunto '{}' ({} bytes) a: {}", attachmentFilename, attachmentBytes.length, to);
        mailSender.send(mimeMessage);
        log.info("Correo con adjunto despachado con éxito a: {}", to);
    }

    private String buildEmailHtml(EmailPayload payload) {
        String client = payload.getCustomerName() != null ? payload.getCustomerName() : "Estimado/a cliente";
        String order = payload.getOrderNumber() != null ? payload.getOrderNumber() : "N/A";
        String status = payload.getNewStatus() != null ? payload.getNewStatus() : "ACTUALIZADO";
        String customBody = payload.getBody() != null ? payload.getBody() : "Tu pedido ha sido actualizado en nuestra plataforma.";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Pedidos360 Café</title>
                </head>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 20px;">
                    <div style="max-width: 600px; margin: auto; border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden;">
                        <div style="background-color: #6F4E37; color: #ffffff; padding: 20px; text-align: center;">
                            <h1 style="margin: 0; font-size: 24px;">☕ Pedidos360 - Café de Especialidad</h1>
                        </div>
                        <div style="padding: 24px;">
                            <p style="font-size: 16px;">¡Hola <strong>%s</strong>!</p>
                            <p>%s</p>
                            <div style="background-color: #f9f6f0; border-left: 4px solid #6F4E37; padding: 12px; margin: 20px 0;">
                                <p style="margin: 0;"><strong>Número de Pedido:</strong> %s</p>
                                <p style="margin: 0;"><strong>Estado:</strong> <span style="color: #6F4E37; font-weight: bold;">%s</span></p>
                            </div>
                            <p style="font-size: 14px; color: #777;">Gracias por elegir la calidad y frescura de Pedidos360.</p>
                        </div>
                        <div style="background-color: #f4f4f4; color: #888; padding: 12px; text-align: center; font-size: 12px;">
                            Este es un correo automático. Por favor no responder a esta casilla.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(client, customBody, order, status);
    }
}
