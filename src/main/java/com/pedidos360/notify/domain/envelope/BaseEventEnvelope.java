package com.pedidos360.notify.domain.envelope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

/**
 * Envelope genérico y estandarizado para eventos y comandos en Pedidos360.
 *
 * @param <T> Tipo de la carga útil (payload) específica del comando
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseEventEnvelope<T> implements Serializable {

    @JsonProperty(value = "type", required = true)
    private String type;

    @JsonProperty(value = "eventId", required = true)
    private String eventId;

    @JsonProperty(value = "timestamp")
    private Instant timestamp;

    @JsonProperty(value = "traceId")
    private String traceId;

    @JsonProperty(value = "correlationId")
    private String correlationId;

    @JsonProperty(value = "payload", required = true)
    private T payload;

    public BaseEventEnvelope() {
    }

    public BaseEventEnvelope(String type, String eventId, Instant timestamp, String traceId, String correlationId, T payload) {
        this.type = type;
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.correlationId = correlationId;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "BaseEventEnvelope{" +
                "type='" + type + '\'' +
                ", eventId='" + eventId + '\'' +
                ", timestamp=" + timestamp +
                ", traceId='" + traceId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", payload=" + payload +
                '}';
    }
}
