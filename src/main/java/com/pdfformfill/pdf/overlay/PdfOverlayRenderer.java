package com.pdfformfill.pdf.overlay;

import com.pdfformfill.dto.FieldDefinition;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders field values onto a PDF at positions defined by (x, y, width, height, page) from the
 * field definition. Does not use AcroForm; draws text directly into the page content stream.
 * Phase 1: Latin-only (PDType1Font Helvetica); coordinates in PDF user space (origin bottom-left).
 */
@Component
public class PdfOverlayRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfOverlayRenderer.class);

    private static final float DEFAULT_FONT_SIZE = 12f;

    /**
     * For each field, draws its value from {@code fieldData} at the field's (page, x, y) with
     * default font size. Ignores width/height for layout in phase 1 (single line, no shrink-to-fit).
     *
     * @param document  loaded PDF (modified in place)
     * @param fields    list of field definitions (name, type, x, y, width, height, page)
     * @param fieldData map from field name to value (Object; will be stringified)
     */
    public void render(PDDocument document, List<FieldDefinition> fields, Map<String, Object> fieldData) throws IOException {
        if (document == null || fields == null || fields.isEmpty()) {
            return;
        }
        Map<Integer, List<FieldDefinition>> byPage = fields.stream()
                .filter(f -> f.page() != null && f.page() >= 1)
                .filter(f -> f.name() != null)
                .filter(f -> f.x() != null && f.y() != null)
                .collect(Collectors.groupingBy(FieldDefinition::page));

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        for (Map.Entry<Integer, List<FieldDefinition>> entry : byPage.entrySet()) {
            int page1Based = entry.getKey();
            int page0Based = page1Based - 1;
            if (page0Based >= document.getNumberOfPages()) {
                log.warn("Page {} exceeds document pages ({}), skip overlay", page1Based, document.getNumberOfPages());
                continue;
            }
            PDPage page = document.getPage(page0Based);
            try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setFont(font, DEFAULT_FONT_SIZE);
                cs.setNonStrokingColor(0f, 0f, 0f);
                for (FieldDefinition field : entry.getValue()) {
                    Object value = fieldData != null ? fieldData.get(field.name()) : null;
                    String text = value != null ? value.toString() : "";
                    if (text.isEmpty()) {
                        continue;
                    }
                    String safe = toLatin1Safe(text);
                    if (safe.isEmpty()) {
                        continue;
                    }
                    try {
                        cs.beginText();
                        cs.newLineAtOffset(field.x().floatValue(), field.y().floatValue());
                        cs.showText(safe);
                        cs.endText();
                    } catch (IOException e) {
                        log.warn("Overlay failed for field '{}': {}", field.name(), e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Phase 1: Helvetica supports only Latin-1. Strip or replace unsupported chars to avoid PDFBox errors.
     */
    private static String toLatin1Safe(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0xFF) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}
