package com.pedidos360.notify.domain.model;

/**
 * Encapsula el documento PDF compilado en memoria.
 */
public class GeneratedPdfDocument {

    private final String filename;
    private final byte[] contentBytes;
    private final String contentType;
    private final long sizeBytes;

    public GeneratedPdfDocument(String filename, byte[] contentBytes) {
        this.filename = filename;
        this.contentBytes = contentBytes;
        this.contentType = "application/pdf";
        this.sizeBytes = contentBytes != null ? contentBytes.length : 0L;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getContentBytes() {
        return contentBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    @Override
    public String toString() {
        return "GeneratedPdfDocument{" +
                "filename='" + filename + '\'' +
                ", sizeBytes=" + sizeBytes +
                '}';
    }
}
