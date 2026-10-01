package com.pedidos360.notify.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Servicio en memoria para garantizar idempotencia y deduplicación de mensajes por eventId.
 * Es compartido entre todos los listeners (EmailListener e InvoiceListener).
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final Cache<String, Instant> processedEventsCache;

    public IdempotencyService(
            @Value("${pedidos360.idempotency.ttl-hours:24}") int ttlHours,
            @Value("${pedidos360.idempotency.max-entries:50000}") long maxEntries) {
        this.processedEventsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(ttlHours))
                .maximumSize(maxEntries)
                .build();
    }

    /**
     * Verifica de manera atómica si el evento ya fue procesado.
     * Si no ha sido procesado, lo marca atómicamente como procesado y retorna true.
     * Si ya había sido procesado previamente, retorna false.
     *
     * @param eventId Identificador único del evento
     * @return true si es la primera vez que se procesa (adquisición exitosa); false si es duplicado.
     */
    public synchronized boolean checkAndMarkProcessed(String eventId) {
        return checkAndMarkProcessed(eventId, "DEFAULT");
    }

    /**
     * Verifica de manera atómica si el evento ya fue procesado para un tipo de flujo.
     *
     * @param eventId  Identificador único del evento
     * @param flowType Tipo de flujo (e.g. "EMAIL", "INVOICE")
     * @return true si es la primera vez que se procesa; false si es duplicado.
     */
    public synchronized boolean checkAndMarkProcessed(String eventId, String flowType) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Se intentó registrar un eventId nulo o vacío para idempotencia (flujo: {})", flowType);
            return true;
        }

        Instant existing = processedEventsCache.getIfPresent(eventId);
        if (existing != null) {
            log.info("Mensaje duplicado detectado para eventId: {} (flujo: {}). Procesado previamente en: {}", eventId, flowType, existing);
            return false;
        }

        processedEventsCache.put(eventId, Instant.now());
        log.debug("eventId {} registrado exitosamente en memoria como procesado (flujo: {})", eventId, flowType);
        return true;
    }

    /**
     * Consulta si un eventId ya se encuentra registrado.
     *
     * @param eventId Identificador único
     * @return true si ya existe en la caché
     */
    public boolean isProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return processedEventsCache.getIfPresent(eventId) != null;
    }

    /**
     * Limpia la caché (útil para pruebas unitarias).
     */
    public void clear() {
        processedEventsCache.invalidateAll();
    }

    /**
     * Retorna el número de elementos actualmente en caché.
     */
    public long size() {
        return processedEventsCache.estimatedSize();
    }
}
