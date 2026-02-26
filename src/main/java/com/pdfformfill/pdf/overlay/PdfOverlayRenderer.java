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
 * Phase 2: single-line shrink-to-fit by width; truncate with "..." if still over at min font size.
 * Latin-only (PDType1Font Helvetica).
 */
@Component
public class PdfOverlayRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfOverlayRenderer.class);

    private static final float DEFAULT_FONT_SIZE = 12f;
    private static final float MIN_FONT_SIZE = 6f;
    private static final String ELLIPSIS = "...";

    /**
     * For each field, draws its value at (page, x, y). If field has width, uses shrink-to-fit
     * (reduce font size until text fits); if still over at min size, truncates with "...".
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
                    Float widthLimit = field.width() != null ? field.width().floatValue() : null;
                    float fontSize = DEFAULT_FONT_SIZE;
                    String toDraw = safe;
                    if (widthLimit != null && widthLimit > 0) {
                        fontSize = shrinkToFit(font, safe, widthLimit);
                        if (textWidthInPoints(font, safe, fontSize) > widthLimit) {
                            toDraw = truncateWithEllipsis(font, safe, fontSize, widthLimit);
                        }
                    }
                    try {
                        cs.setFont(font, fontSize);
                        cs.beginText();
                        cs.newLineAtOffset(field.x().floatValue(), field.y().floatValue());
                        cs.showText(toDraw);
                        cs.endText();
                    } catch (IOException e) {
                        log.warn("Overlay failed for field '{}': {}", field.name(), e.getMessage());
                    }
                }
            }
        }
    }

    /** Width of text in points (font size applied). */
    private static float textWidthInPoints(PDType1Font font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    /** Largest font size in [MIN_FONT_SIZE, DEFAULT_FONT_SIZE] such that text width <= widthLimit. */
    private static float shrinkToFit(PDType1Font font, String text, float widthLimit) throws IOException {
        float size = DEFAULT_FONT_SIZE;
        while (size >= MIN_FONT_SIZE && textWidthInPoints(font, text, size) > widthLimit) {
            size -= 1f;
        }
        return Math.max(size, MIN_FONT_SIZE);
    }

    /** Truncate text so that (text + ELLIPSIS) fits in widthLimit at given fontSize. */
    private static String truncateWithEllipsis(PDType1Font font, String text, float fontSize, float widthLimit) throws IOException {
        float ellipsisWidth = textWidthInPoints(font, ELLIPSIS, fontSize);
        float maxTextWidth = widthLimit - ellipsisWidth;
        if (maxTextWidth <= 0) {
            return ELLIPSIS;
        }
        String result = text;
        while (result.length() > 0 && textWidthInPoints(font, result, fontSize) > maxTextWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ELLIPSIS;
    }

    /**
     * Helvetica supports only Latin-1. Replace unsupported chars to avoid PDFBox errors.
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
