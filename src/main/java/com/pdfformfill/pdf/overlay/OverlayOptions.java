package com.pdfformfill.pdf.overlay;

import com.pdfformfill.dto.FieldsDefinition;

/**
 * 从导入的 JSON（FieldsDefinition）解析出的 overlay 渲染选项，用于替代写死的常量。
 */
public record OverlayOptions(
        Double scale,
        String checkboxImagePath,
        float fontSize,
        float minFontSize,
        float[] fontColorRgb,
        float paddingX,
        float paddingY
) {
    /** Defaults used when imported JSON does not specify values. */
    public static final float DEFAULT_FONT_SIZE = 12f;
    public static final float DEFAULT_MIN_FONT_SIZE = 6f;
    public static final float DEFAULT_PADDING_X = 3f;
    public static final float DEFAULT_PADDING_Y = 0f;
    public static final float[] DEFAULT_FONT_COLOR_RGB = new float[]{0f, 0f, 0f};

    /**
     * Build options from imported definition; use defaults for any null. Empty checkbox path
     * is replaced by defaultCheckboxPath.
     */
    public static OverlayOptions from(FieldsDefinition def, String defaultCheckboxPath) {
        Double scale = def != null && def.scale() != null && def.scale() > 0 ? def.scale() : null;
        String checkbox = def != null && def.checkboxCheckedImage() != null && !def.checkboxCheckedImage().isBlank()
                ? def.checkboxCheckedImage()
                : defaultCheckboxPath;
        float fontSize = def != null && def.fontSize() != null && def.fontSize() > 0
                ? def.fontSize().floatValue()
                : DEFAULT_FONT_SIZE;
        float[] rgb = parseHexColor(def != null ? def.fontColor() : null);
        float padX = def != null && def.paddingX() != null && def.paddingX() >= 0
                ? def.paddingX().floatValue()
                : DEFAULT_PADDING_X;
        float padY = def != null && def.paddingY() != null && def.paddingY() >= 0
                ? def.paddingY().floatValue()
                : DEFAULT_PADDING_Y;
        return new OverlayOptions(scale, checkbox, fontSize, DEFAULT_MIN_FONT_SIZE, rgb, padX, padY);
    }

    /** Parse "#RRGGBB" or "#RGB" to RGB in [0,1]; invalid input returns black. */
    public static float[] parseHexColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return DEFAULT_FONT_COLOR_RGB.clone();
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 6) {
            try {
                int r = Integer.parseInt(s.substring(0, 2), 16);
                int g = Integer.parseInt(s.substring(2, 4), 16);
                int b = Integer.parseInt(s.substring(4, 6), 16);
                return new float[]{r / 255f, g / 255f, b / 255f};
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        if (s.length() == 3) {
            try {
                int r = Integer.parseInt(s.substring(0, 1) + s.substring(0, 1), 16);
                int g = Integer.parseInt(s.substring(1, 2) + s.substring(1, 2), 16);
                int b = Integer.parseInt(s.substring(2, 3) + s.substring(2, 3), 16);
                return new float[]{r / 255f, g / 255f, b / 255f};
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return DEFAULT_FONT_COLOR_RGB.clone();
    }
}
