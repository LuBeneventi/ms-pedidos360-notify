package com.pedidos360.notify.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Detalle de cada ítem de café o producto facturado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoiceItem implements Serializable {

    @JsonProperty(value = "description", required = true)
    private String description;

    @JsonProperty(value = "quantity", required = true)
    private Integer quantity;

    @JsonProperty(value = "unitPrice", required = true)
    private BigDecimal unitPrice;

    @JsonProperty(value = "totalPrice", required = true)
    private BigDecimal totalPrice;

    public InvoiceItem() {
    }

    public InvoiceItem(String description, Integer quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    @Override
    public String toString() {
        return "InvoiceItem{" +
                "description='" + description + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                ", totalPrice=" + totalPrice +
                '}';
    }
}
