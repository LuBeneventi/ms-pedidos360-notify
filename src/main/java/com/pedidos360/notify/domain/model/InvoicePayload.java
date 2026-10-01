package com.pedidos360.notify.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Carga útil para comandos de facturación en la cola q.cmd.invoice.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoicePayload implements Serializable {

    @JsonProperty(value = "orderId", required = true)
    private String orderId;

    @JsonProperty(value = "invoiceNumber", required = true)
    private String invoiceNumber;

    @JsonProperty("invoiceType")
    private String invoiceType = "BOLETA";

    @JsonProperty("issueDate")
    private Instant issueDate;

    @JsonProperty(value = "customer", required = true)
    private CustomerInfo customer;

    @JsonProperty(value = "items", required = true)
    private List<InvoiceItem> items;

    @JsonProperty("currency")
    private String currency = "CLP";

    @JsonProperty(value = "subtotal", required = true)
    private BigDecimal subtotal;

    @JsonProperty(value = "tax", required = true)
    private BigDecimal tax;

    @JsonProperty(value = "total", required = true)
    private BigDecimal total;

    public InvoicePayload() {
    }

    public InvoicePayload(String orderId, String invoiceNumber, String invoiceType, Instant issueDate, CustomerInfo customer, List<InvoiceItem> items, String currency, BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
        this.orderId = orderId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceType = invoiceType;
        this.issueDate = issueDate;
        this.customer = customer;
        this.items = items;
        this.currency = currency;
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = total;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(String invoiceType) {
        this.invoiceType = invoiceType;
    }

    public Instant getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(Instant issueDate) {
        this.issueDate = issueDate;
    }

    public CustomerInfo getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerInfo customer) {
        this.customer = customer;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public void setItems(List<InvoiceItem> items) {
        this.items = items;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    @Override
    public String toString() {
        return "InvoicePayload{" +
                "orderId='" + orderId + '\'' +
                ", invoiceNumber='" + invoiceNumber + '\'' +
                ", invoiceType='" + invoiceType + '\'' +
                ", total=" + total +
                '}';
    }
}
