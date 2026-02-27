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
 * field definition. Options (fontSize, fontColor, padding) come from imported JSON.
 * Coordinate system: definition (x, y) is top-left origin, y downward; converted to PDF user space.
 */
@Component
public class PdfOverlayRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfOverlayRenderer.class);

    private static final String ELLIPSIS = "...";
    /** Default line height when field.height is null (for baseline placement). */
    private static final float DEFAULT_LINE_HEIGHT_FACTOR = 1.2f;

    /**
     * For each field, draws its value at (page, x, y). Options (fontSize, fontColor, paddingX, paddingY, checkbox path)
     * come from imported JSON via {@link OverlayOptions}; scale converts viewport pixels to PDF points when present.
     *
     * @param document  loaded PDF (modified in place)
     * @param fields    list of field definitions (name, type, x, y, width, height, page, optional verticalAlign)
     * @param fieldData map from field name to value (Object; will be stringified)
     * @param options   overlay options from FieldsDefinition (scale, checkbox path, fontSize, color, padding)
     */
    public void render(PDDocument document, List<FieldDefinition> fields, Map<String, Object> fieldData, OverlayOptions options) throws IOException {
        if (document == null || fields == null || fields.isEmpty() || options == null) {
            return;
        }
        Map<Integer, List<FieldDefinition>> byPage = fields.stream()
                .filter(f -> f.page() != null && f.page() >= 1)
                .filter(f -> f.name() != null)
                .filter(f -> f.x() != null && f.y() != null)
                .collect(Collectors.groupingBy(FieldDefinition::page));

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDImageXObject checkboxImage = loadCheckboxImage(document, options.checkboxImagePath());

        float scale = (options.scale() != null && options.scale() > 0) ? options.scale().floatValue() : 1f;
        float defaultFontSize = options.fontSize();
        float minFontSize = options.minFontSize();
        float[] colorRgb = options.fontColorRgb();
        float paddingX = options.paddingX();
        float paddingY = options.paddingY();

        for (Map.Entry<Integer, List<FieldDefinition>> entry : byPage.entrySet()) {
            int page1Based = entry.getKey();
            int page0Based = page1Based - 1;
            if (page0Based >= document.getNumberOfPages()) {
                log.warn("Page {} exceeds document pages ({}), skip overlay", page1Based, document.getNumberOfPages());
                continue;
            }
            PDPage page = document.getPage(page0Based);
            float pageHeight = page.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                if (colorRgb != null && colorRgb.length >= 3) {
                    cs.setNonStrokingColor(colorRgb[0], colorRgb[1], colorRgb[2]);
                }
                for (FieldDefinition field : entry.getValue()) {
                    Object value = fieldData != null ? fieldData.get(field.name()) : null;
                    String type = field.type() != null ? field.type().toLowerCase() : "";

                    if (isCheckboxOrBoolean(type)) {
                        if (Boolean.TRUE.equals(value) && checkboxImage != null) {
                            float xPt = field.x().floatValue() / scale;
                            float yDefPt = field.y().floatValue() / scale;
                            float widthPt = field.width() != null && field.width().floatValue() > 0
                                    ? field.width().floatValue() / scale : 16f;
                            float heightPt = field.height() != null && field.height().floatValue() > 0
                                    ? field.height().floatValue() / scale : 16f;
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
                    float xPt = field.x().floatValue() / scale;
                    float yDefPt = field.y().floatValue() / scale;
                    float widthPt = field.width() != null ? field.width().floatValue() / scale : 0f;
                    float heightPt = field.height() != null ? field.height().floatValue() / scale : (defaultFontSize * DEFAULT_LINE_HEIGHT_FACTOR);

                    float textWidthLimit = widthPt > 2 * paddingX
                            ? widthPt - 2 * paddingX
                            : (widthPt > 0 ? widthPt * 0.5f : 0f);
                    Float widthLimit = field.width() != null && textWidthLimit > 0 ? textWidthLimit : null;
                    float fontSize = defaultFontSize;
                    String toDraw = safe;
                    if (widthLimit != null && widthLimit > 0) {
                        fontSize = shrinkToFit(font, safe, widthLimit, defaultFontSize, minFontSize);
                        if (textWidthInPoints(font, safe, fontSize) > widthLimit) {
                            toDraw = truncateWithEllipsis(font, safe, fontSize, widthLimit);
                        }
                    }
                    float rectHeight = field.height() != null ? heightPt : (fontSize * DEFAULT_LINE_HEIGHT_FACTOR);
                    String verticalAlign = field.verticalAlign() != null ? field.verticalAlign().toLowerCase() : "middle";
                    float yBaseline = baselineForVerticalAlign(pageHeight, yDefPt, rectHeight, fontSize, font, paddingY, verticalAlign);
                    float textX = xPt + paddingX;

                    try {
                        cs.setFont(font, fontSize);
                        cs.beginText();
                        cs.newLineAtOffset(textX, yBaseline);
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
     * Computes the PDF y-coordinate for the text baseline. Definition uses top-left origin with y downward.
     * verticalAlign "top": top of text at yDefPt + paddingY; "middle" (default): text centered in rect (with paddingY as margin).
     */
    private static float baselineForVerticalAlign(float pageHeight, float yDefPt, float rectHeight, float fontSize, PDFont font, float paddingY, String verticalAlign) {
        float ascentPt, descentPt;
        if (font.getFontDescriptor() != null) {
            ascentPt = fontSize * font.getFontDescriptor().getAscent() / 1000f;
            descentPt = fontSize * font.getFontDescriptor().getDescent() / 1000f;
        } else {
            ascentPt = fontSize * 0.718f;
            descentPt = fontSize * -0.176f;
        }
        if ("top".equals(verticalAlign)) {
            float yTopDef = yDefPt + paddingY;
            float yTopPdf = pageHeight - yTopDef;
            return yTopPdf - ascentPt;
        }
        float rectCenterY = pageHeight - yDefPt - rectHeight / 2f;
        float textVerticalCenterOffset = (ascentPt + descentPt) / 2f;
        return rectCenterY - textVerticalCenterOffset;
    }

    /** Width of text in points (font size applied). */
    private static float textWidthInPoints(PDType1Font font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    /** Largest font size in [minFontSize, defaultFontSize] such that text width <= widthLimit. */
    private static float shrinkToFit(PDType1Font font, String text, float widthLimit, float defaultFontSize, float minFontSize) throws IOException {
        float size = defaultFontSize;
        while (size >= minFontSize && textWidthInPoints(font, text, size) > widthLimit) {
            size -= 1f;
        }
        return Math.max(size, minFontSize);
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
