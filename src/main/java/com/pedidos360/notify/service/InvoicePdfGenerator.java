package com.pedidos360.notify.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pedidos360.notify.domain.model.CustomerInfo;
import com.pedidos360.notify.domain.model.GeneratedPdfDocument;
import com.pedidos360.notify.domain.model.InvoiceItem;
import com.pedidos360.notify.domain.model.InvoicePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generador de comprobantes tributarios (Boleta / Factura) en formato PDF
 * utilizando la biblioteca OpenPDF en memoria.
 */
@Service
public class InvoicePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    /**
     * Genera un comprobante en PDF a partir del payload del pedido.
     *
     * @param invoice Datos del pedido y facturación
     * @return GeneratedPdfDocument con el nombre y los bytes generados
     */
    public GeneratedPdfDocument generateInvoicePdf(InvoicePayload invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("InvoicePayload no puede ser nulo");
        }
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            throw new IllegalArgumentException("El número de comprobante es requerido");
        }
        if (invoice.getItems() == null || invoice.getItems().isEmpty()) {
            throw new IllegalArgumentException("El comprobante debe contener al menos un ítem");
        }

        log.info("Iniciando generación de PDF para comprobante: {} (Orden: {})", invoice.getInvoiceNumber(), invoice.getOrderId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Estilos y fuentes
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(111, 78, 55));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);

            // 1. Encabezado de la empresa y folio
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{60, 40});

            PdfPCell companyCell = new PdfPCell();
            companyCell.setBorder(Rectangle.NO_BORDER);
            companyCell.addElement(new Paragraph("PEDIDOS360", titleFont));
            companyCell.addElement(new Paragraph("Café de Especialidad & Tostaduría", subTitleFont));
            companyCell.addElement(new Paragraph("RUT: 76.999.888-0\nAv. Providencia 1234, Santiago, Chile\ncontacto@pedidos360.com", regularFont));
            headerTable.addCell(companyCell);

            String docType = invoice.getInvoiceType() != null ? invoice.getInvoiceType().toUpperCase() : "BOLETA";
            PdfPCell docCell = new PdfPCell();
            docCell.setBorder(Rectangle.BOX);
            docCell.setBorderColor(new Color(111, 78, 55));
            docCell.setBorderWidth(1.5f);
            docCell.setPadding(10);
            docCell.setHorizontalAlignment(Element.ALIGN_CENTER);

            Paragraph docTitle = new Paragraph(docType + " ELECTRÓNICA", subTitleFont);
            docTitle.setAlignment(Element.ALIGN_CENTER);
            Paragraph docFolio = new Paragraph("N° " + invoice.getInvoiceNumber(), titleFont);
            docFolio.setAlignment(Element.ALIGN_CENTER);
            String issueDateStr = invoice.getIssueDate() != null ? DATE_FORMATTER.format(invoice.getIssueDate()) : "Fecha: Emisión Inmediata";
            Paragraph docDate = new Paragraph(issueDateStr, regularFont);
            docDate.setAlignment(Element.ALIGN_CENTER);

            docCell.addElement(docTitle);
            docCell.addElement(docFolio);
            docCell.addElement(docDate);
            headerTable.addCell(docCell);

            document.add(headerTable);
            document.add(new Paragraph(" "));

            // 2. Información del cliente y del pedido
            CustomerInfo customer = invoice.getCustomer();
            PdfPTable clientTable = new PdfPTable(2);
            clientTable.setWidthPercentage(100);
            clientTable.setWidths(new float[]{50, 50});

            PdfPCell clientCell = new PdfPCell();
            clientCell.setBorder(Rectangle.BOX);
            clientCell.setBorderColor(Color.LIGHT_GRAY);
            clientCell.setPadding(8);
            clientCell.addElement(new Paragraph("DATOS DEL CLIENTE", boldFont));
            if (customer != null) {
                clientCell.addElement(new Paragraph("Nombre: " + (customer.getName() != null ? customer.getName() : "Consumidor Final"), regularFont));
                if (customer.getTaxId() != null && !customer.getTaxId().isBlank()) {
                    clientCell.addElement(new Paragraph("RUT/DNI: " + customer.getTaxId(), regularFont));
                }
                clientCell.addElement(new Paragraph("Email: " + customer.getEmail(), regularFont));
                if (customer.getAddress() != null && !customer.getAddress().isBlank()) {
                    clientCell.addElement(new Paragraph("Dirección: " + customer.getAddress(), regularFont));
                }
            }
            clientTable.addCell(clientCell);

            PdfPCell orderCell = new PdfPCell();
            orderCell.setBorder(Rectangle.BOX);
            orderCell.setBorderColor(Color.LIGHT_GRAY);
            orderCell.setPadding(8);
            orderCell.addElement(new Paragraph("DATOS DE LA VENTA", boldFont));
            orderCell.addElement(new Paragraph("Pedido N°: " + invoice.getOrderId(), regularFont));
            orderCell.addElement(new Paragraph("Moneda: " + (invoice.getCurrency() != null ? invoice.getCurrency() : "CLP"), regularFont));
            orderCell.addElement(new Paragraph("Medio de Pago: Pago en Línea Confirmado", regularFont));
            clientTable.addCell(orderCell);

            document.add(clientTable);
            document.add(new Paragraph(" "));

            // 3. Tabla de Productos
            PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{50, 15, 15, 20});

            // Cabeceras de tabla
            addHeaderCell(itemsTable, "Descripción", tableHeaderFont);
            addHeaderCell(itemsTable, "Cantidad", tableHeaderFont);
            addHeaderCell(itemsTable, "P. Unitario", tableHeaderFont);
            addHeaderCell(itemsTable, "Total", tableHeaderFont);

            NumberFormat currencyFormat = NumberFormat.getNumberInstance(Locale.GERMANY);

            for (InvoiceItem item : invoice.getItems()) {
                addBodyCell(itemsTable, item.getDescription() != null ? item.getDescription() : "Café Pedidos360", regularFont, Element.ALIGN_LEFT);
                addBodyCell(itemsTable, String.valueOf(item.getQuantity() != null ? item.getQuantity() : 1), regularFont, Element.ALIGN_CENTER);
                String unitPriceStr = item.getUnitPrice() != null ? currencyFormat.format(item.getUnitPrice()) : "0";
                addBodyCell(itemsTable, unitPriceStr, regularFont, Element.ALIGN_RIGHT);
                String totalPriceStr = item.getTotalPrice() != null ? currencyFormat.format(item.getTotalPrice()) : "0";
                addBodyCell(itemsTable, totalPriceStr, regularFont, Element.ALIGN_RIGHT);
            }

            document.add(itemsTable);
            document.add(new Paragraph(" "));

            // 4. Totales Financieros
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(45);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.setWidths(new float[]{50, 50});

            addTotalRow(totalsTable, "Subtotal:", invoice.getSubtotal(), currencyFormat, regularFont);
            addTotalRow(totalsTable, "IVA (19%):", invoice.getTax(), currencyFormat, regularFont);
            addTotalRow(totalsTable, "TOTAL:", invoice.getTotal(), currencyFormat, boldFont);

            document.add(totalsTable);

            // 5. Pie de página
            Paragraph footer = new Paragraph("\n\nGracias por su compra en Pedidos360. Documento tributario emitido electrónicamente.", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();

            byte[] pdfBytes = baos.toByteArray();
            String filename = docType.toLowerCase() + "_" + invoice.getInvoiceNumber().replaceAll("[^a-zA-Z0-9.-]", "_") + ".pdf";
            log.info("PDF generado exitosamente para folio {}: {} bytes", invoice.getInvoiceNumber(), pdfBytes.length);

            return new GeneratedPdfDocument(filename, pdfBytes);

        } catch (Exception e) {
            log.error("Error al generar PDF de comprobante para {}", invoice.getInvoiceNumber(), e);
            throw new RuntimeException("Error al compilar PDF de boleta/factura: " + e.getMessage(), e);
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(111, 78, 55));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, BigDecimal amount, NumberFormat format, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        String amountStr = amount != null ? format.format(amount) : "0";
        PdfPCell amountCell = new PdfPCell(new Phrase("$ " + amountStr, font));
        amountCell.setBorder(Rectangle.NO_BORDER);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountCell.setPadding(4);
        table.addCell(amountCell);
    }
}
