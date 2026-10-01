package com.pedidos360.notify.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Información del cliente receptor de la boleta o factura.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerInfo implements Serializable {

    @JsonProperty(value = "name", required = true)
    private String name;

    @JsonProperty("taxId")
    private String taxId;

    @JsonProperty(value = "email", required = true)
    private String email;

    @JsonProperty("address")
    private String address;

    public CustomerInfo() {
    }

    public CustomerInfo(String name, String taxId, String email, String address) {
        this.name = name;
        this.taxId = taxId;
        this.email = email;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "CustomerInfo{" +
                "name='" + name + '\'' +
                ", taxId='" + taxId + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
