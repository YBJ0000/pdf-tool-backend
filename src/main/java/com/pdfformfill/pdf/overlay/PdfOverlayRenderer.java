package com.pdfformfill.pdf.overlay;

import com.pdfformfill.dto.FieldDefinition;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders field values onto a PDF at positions defined by (x, y, width, height, page) from the
 * field definition. Does not use AcroForm; draws text directly into the page content stream.
 * Coordinate system: definition (x, y) is treated as top-left origin, y downward (e.g. from
 * issue-115); converted to PDF user space (origin bottom-left, y upward) using page height.
 */
@Component
public class PdfOverlayRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfOverlayRenderer.class);

    private static final float DEFAULT_FONT_SIZE = 12f;
    private static final float MIN_FONT_SIZE = 6f;
    private static final String ELLIPSIS = "...";
    /** Default line height when field.height is null (for baseline placement). */
    private static final float DEFAULT_LINE_HEIGHT_FACTOR = 1.2f;

    /**
     * For each field, draws its value at (page, x, y). If field has width, uses shrink-to-fit
     * (reduce font size until text fits); if still over at min size, truncates with "...".
     * Coordinates in the definition are treated as top-left origin, y downward. If {@code scale}
     * is present and &gt; 0, x/y/width/height are treated as viewport pixels (1 PDF point = scale pixels);
     * otherwise they are treated as PDF points.
     *
     * @param document  loaded PDF (modified in place)
     * @param fields    list of field definitions (name, type, x, y, width, height, page)
     * @param fieldData map from field name to value (Object; will be stringified)
     * @param scale     optional scale from frontend (viewport pixels per PDF point); null or &lt;= 0 means coordinates are already in PDF points
     * @param checkboxCheckedImagePath optional path to image for checked checkbox/boolean (classpath:xxx or file path); null/empty = draw as text
     */
    public void render(PDDocument document, List<FieldDefinition> fields, Map<String, Object> fieldData, Double scale, String checkboxCheckedImagePath) throws IOException {
        if (document == null || fields == null || fields.isEmpty()) {
            return;
        }
        Map<Integer, List<FieldDefinition>> byPage = fields.stream()
                .filter(f -> f.page() != null && f.page() >= 1)
                .filter(f -> f.name() != null)
                .filter(f -> f.x() != null && f.y() != null)
                .collect(Collectors.groupingBy(FieldDefinition::page));

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDImageXObject checkboxImage = loadCheckboxImage(document, checkboxCheckedImagePath);

        for (Map.Entry<Integer, List<FieldDefinition>> entry : byPage.entrySet()) {
            int page1Based = entry.getKey();
            int page0Based = page1Based - 1;
            if (page0Based >= document.getNumberOfPages()) {
                log.warn("Page {} exceeds document pages ({}), skip overlay", page1Based, document.getNumberOfPages());
                continue;
            }
            PDPage page = document.getPage(page0Based);
            float pageHeight = page.getMediaBox().getHeight();
            float s = (scale != null && scale > 0) ? scale.floatValue() : 1f;

            try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(0f, 0f, 0f);
                for (FieldDefinition field : entry.getValue()) {
                    Object value = fieldData != null ? fieldData.get(field.name()) : null;
                    String type = field.type() != null ? field.type().toLowerCase() : "";

                    if (isCheckboxOrBoolean(type)) {
                        if (Boolean.TRUE.equals(value) && checkboxImage != null) {
                            float xPt = field.x().floatValue() / s;
                            float yDefPt = field.y().floatValue() / s;
                            float widthPt = field.width() != null && field.width().floatValue() > 0
                                    ? field.width().floatValue() / s : 16f;
                            float heightPt = field.height() != null && field.height().floatValue() > 0
                                    ? field.height().floatValue() / s : 16f;
                            float yPdf = pageHeight - yDefPt - heightPt;
                            try {
                                cs.drawImage(checkboxImage, xPt, yPdf, widthPt, heightPt);
                            } catch (IOException e) {
                                log.warn("Draw checkbox image failed for field '{}': {}", field.name(), e.getMessage());
                            }
                        }
                        continue;
                    }

                    String text = value != null ? value.toString() : "";
                    if (text.isEmpty()) {
                        continue;
                    }
                    String safe = toLatin1Safe(text);
                    if (safe.isEmpty()) {
                        continue;
                    }
                    float xPt = field.x().floatValue() / s;
                    float yDefPt = field.y().floatValue() / s;
                    float widthPt = field.width() != null ? field.width().floatValue() / s : 0f;
                    float heightPt = field.height() != null ? field.height().floatValue() / s : (DEFAULT_FONT_SIZE * DEFAULT_LINE_HEIGHT_FACTOR);

                    Float widthLimit = field.width() != null && widthPt > 0 ? widthPt : null;
                    float fontSize = DEFAULT_FONT_SIZE;
                    String toDraw = safe;
                    if (widthLimit != null && widthLimit > 0) {
                        fontSize = shrinkToFit(font, safe, widthLimit);
                        if (textWidthInPoints(font, safe, fontSize) > widthLimit) {
                            toDraw = truncateWithEllipsis(font, safe, fontSize, widthLimit);
                        }
                    }
                    float rectHeight = field.height() != null ? heightPt : (fontSize * DEFAULT_LINE_HEIGHT_FACTOR);
                    float yBaseline = baselineForVerticalCenter(pageHeight, yDefPt, rectHeight, fontSize, font);

                    try {
                        cs.setFont(font, fontSize);
                        cs.beginText();
                        cs.newLineAtOffset(xPt, yBaseline);
                        cs.showText(toDraw);
                        cs.endText();
                    } catch (IOException e) {
                        log.warn("Overlay failed for field '{}': {}", field.name(), e.getMessage());
                    }
                }
            }
        }
    }

    private static boolean isCheckboxOrBoolean(String type) {
        return "checkbox".equals(type) || "boolean".equals(type);
    }

    /**
     * Loads an image from classpath:name or file path. Returns null on failure or if path is null/blank.
     */
    private PDImageXObject loadCheckboxImage(PDDocument document, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            BufferedImage bim;
            if (path.startsWith("classpath:")) {
                String name = path.substring("classpath:".length()).trim();
                try (InputStream in = getClass().getResourceAsStream("/" + name)) {
                    if (in == null) {
                        log.warn("Checkbox image not found on classpath: {}", name);
                        return null;
                    }
                    bim = ImageIO.read(in);
                }
            } else {
                File file = new File(path);
                if (!file.isAbsolute()) {
                    file = new File(System.getProperty("user.dir", ""), path);
                }
                if (!file.isFile()) {
                    log.warn("Checkbox image file not found: {}", file.getAbsolutePath());
                    return null;
                }
                bim = ImageIO.read(file);
            }
            if (bim == null) {
                log.warn("Could not decode checkbox image: {}", path);
                return null;
            }
            return LosslessFactory.createFromImage(document, bim);
        } catch (IOException e) {
            log.warn("Failed to load checkbox image from {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Computes the PDF y-coordinate for the text baseline so that the text is vertically centered
     * in the rectangle. Definition uses top-left origin with y downward; rect is (yDefPt, yDefPt + rectHeight).
     */
    private static float baselineForVerticalCenter(float pageHeight, float yDefPt, float rectHeight, float fontSize, PDFont font) {
        float rectCenterY = pageHeight - yDefPt - rectHeight / 2f;
        float ascentPt, descentPt;
        if (font.getFontDescriptor() != null) {
            ascentPt = fontSize * font.getFontDescriptor().getAscent() / 1000f;
            descentPt = fontSize * font.getFontDescriptor().getDescent() / 1000f;
        } else {
            ascentPt = fontSize * 0.718f;
            descentPt = fontSize * -0.176f;
        }
        float textVerticalCenterOffset = (ascentPt + descentPt) / 2f;
        return rectCenterY - textVerticalCenterOffset;
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
