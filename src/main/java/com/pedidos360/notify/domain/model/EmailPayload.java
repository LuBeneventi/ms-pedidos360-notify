package com.pedidos360.notify.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;

/**
 * Carga útil para comandos de envío de correo en la cola q.cmd.email.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailPayload implements Serializable {

    @JsonProperty(value = "to", required = true)
    private String to;

    @JsonProperty(value = "subject", required = true)
    private String subject;

    @JsonProperty(value = "orderNumber", required = true)
    private String orderNumber;

    @JsonProperty("customerName")
    private String customerName;

    @JsonProperty("newStatus")
    private String newStatus;

    @JsonProperty("body")
    private String body;

    @JsonProperty("templateParams")
    private Map<String, Object> templateParams;

    public EmailPayload() {
    }

    public EmailPayload(String to, String subject, String orderNumber, String customerName, String newStatus, String body, Map<String, Object> templateParams) {
        this.to = to;
        this.subject = subject;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.newStatus = newStatus;
        this.body = body;
        this.templateParams = templateParams;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, Object> getTemplateParams() {
        return templateParams;
    }

    public void setTemplateParams(Map<String, Object> templateParams) {
        this.templateParams = templateParams;
    }

    @Override
    public String toString() {
        return "EmailPayload{" +
                "to='" + to + '\'' +
                ", subject='" + subject + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", customerName='" + customerName + '\'' +
                ", newStatus='" + newStatus + '\'' +
                '}';
    }
}
