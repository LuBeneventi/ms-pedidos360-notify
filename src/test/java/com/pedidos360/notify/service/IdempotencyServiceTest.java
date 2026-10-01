package com.pedidos360.notify.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(24, 1000);
    }

    @Test
    @DisplayName("Debe aceptar y registrar un nuevo eventId por primera vez")
    void testFirstTimeEventAccepted() {
        String eventId = UUID.randomUUID().toString();

        boolean result = idempotencyService.checkAndMarkProcessed(eventId);

        assertTrue(result, "El primer intento debe retornar true");
        assertTrue(idempotencyService.isProcessed(eventId), "El evento debe figurar como procesado");
    }

    @Test
    @DisplayName("Debe rechazar y detectar como duplicado un eventId ya procesado")
    void testDuplicateEventRejected() {
        String eventId = "EVENT-TEST-123";

        boolean firstAttempt = idempotencyService.checkAndMarkProcessed(eventId);
        boolean secondAttempt = idempotencyService.checkAndMarkProcessed(eventId);

        assertTrue(firstAttempt, "La primera llamada debe ser aceptada");
        assertFalse(secondAttempt, "La segunda llamada debe ser rechazada por duplicada");
    }

    @Test
    @DisplayName("Debe permitir procesar múltiples eventIds distintos")
    void testMultipleDistinctEventsAccepted() {
        String event1 = "EVT-1";
        String event2 = "EVT-2";

        assertTrue(idempotencyService.checkAndMarkProcessed(event1));
        assertTrue(idempotencyService.checkAndMarkProcessed(event2));
        assertEquals(2, idempotencyService.size());
    }

    @Test
    @DisplayName("Garantía de atomicidad ante concurrencia: solo un hilo debe procesar el mismo eventId")
    void testConcurrentExecutionSameEvent() throws InterruptedException {
        String eventId = "CONCURRENT-EVT-999";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger acceptedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (idempotencyService.checkAndMarkProcessed(eventId)) {
                        acceptedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Arrancan todos a la vez
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, acceptedCount.get(), "Exactamente 1 hilo debe haber obtenido true ante re-entregas simultáneas");
    }
}
