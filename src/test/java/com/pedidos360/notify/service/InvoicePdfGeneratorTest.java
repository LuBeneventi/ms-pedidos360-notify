package com.pedidos360.notify.service;

import com.pedidos360.notify.domain.model.CustomerInfo;
import com.pedidos360.notify.domain.model.GeneratedPdfDocument;
import com.pedidos360.notify.domain.model.InvoiceItem;
import com.pedidos360.notify.domain.model.InvoicePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePdfGeneratorTest {

    private InvoicePdfGenerator pdfGenerator;

    @BeforeEach
    void setUp() {
        pdfGenerator = new InvoicePdfGenerator();
    }

    @Test
    @DisplayName("Debe generar exitosamente un archivo PDF en memoria con el contenido del pedido")
    void testGenerateInvoicePdfSuccess() {
        CustomerInfo customer = new CustomerInfo("Sofía Morales", "18.765.432-1", "sofia@cliente.com", "Av. Providencia 1234");
        List<InvoiceItem> items = List.of(
                new InvoiceItem("Café Grano Geisha 250g - Origen Huila", 2, new BigDecimal("7500.00"), new BigDecimal("15000.00")),
                new InvoiceItem("Filtros V60 Cafetera", 1, new BigDecimal("4500.00"), new BigDecimal("4500.00"))
        );
        InvoicePayload invoice = new InvoicePayload(
                "PED-1024",
                "BOL-2026-00451",
                "BOLETA",
                Instant.now(),
                customer,
                items,
                "CLP",
                new BigDecimal("19500.00"),
                new BigDecimal("3705.00"),
                new BigDecimal("23205.00")
        );

        GeneratedPdfDocument pdfDoc = pdfGenerator.generateInvoicePdf(invoice);

        assertNotNull(pdfDoc, "El documento generado no debe ser nulo");
        assertNotNull(pdfDoc.getContentBytes(), "Los bytes del PDF no deben ser nulos");
        assertTrue(pdfDoc.getSizeBytes() > 1000, "El PDF generado debe contener datos binarios válidos (>1KB)");
        assertEquals("application/pdf", pdfDoc.getContentType());
        assertTrue(pdfDoc.getFilename().endsWith(".pdf"), "El nombre de archivo debe tener extensión .pdf");
        assertTrue(pdfDoc.getFilename().contains("BOL-2026-00451"), "El nombre debe incluir el folio del comprobante");

        // Validar cabecera mágica estándar de PDF (%PDF)
        byte[] bytes = pdfDoc.getContentBytes();
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    @Test
    @DisplayName("Debe fallar al intentar generar un PDF sin ítems o con payload nulo")
    void testGeneratePdfValidationFailure() {
        assertThrows(IllegalArgumentException.class, () -> pdfGenerator.generateInvoicePdf(null));

        CustomerInfo customer = new CustomerInfo("Test", "1-9", "t@c.com", "dir");
        InvoicePayload invoiceNoItems = new InvoicePayload(
                "PED-1", "BOL-1", "BOLETA", Instant.now(), customer, List.of(), "CLP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        assertThrows(IllegalArgumentException.class, () -> pdfGenerator.generateInvoicePdf(invoiceNoItems));
    }
}
