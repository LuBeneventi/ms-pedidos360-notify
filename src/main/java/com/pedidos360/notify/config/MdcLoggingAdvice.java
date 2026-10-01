package com.pedidos360.notify.config;

import com.pedidos360.notify.domain.envelope.BaseEventEnvelope;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Componente utilitario para propagar y limpiar los identificadores de observabilidad distribuida
 * (traceId, correlationId, eventId) en el Mapped Diagnostic Context (MDC) de SLF4J / Logback.
 */
@Component
public class MdcLoggingAdvice {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String EVENT_ID_KEY = "eventId";

    public void populateMdc(BaseEventEnvelope<?> envelope) {
        if (envelope == null) {
            return;
        }
        if (envelope.getTraceId() != null) {
            MDC.put(TRACE_ID_KEY, envelope.getTraceId());
        }
        if (envelope.getCorrelationId() != null) {
            MDC.put(CORRELATION_ID_KEY, envelope.getCorrelationId());
        }
        if (envelope.getEventId() != null) {
            MDC.put(EVENT_ID_KEY, envelope.getEventId());
        }
    }

    public void clearMdc() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(EVENT_ID_KEY);
    }
}
