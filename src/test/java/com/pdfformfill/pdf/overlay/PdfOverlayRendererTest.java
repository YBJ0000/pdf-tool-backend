package com.pdfformfill.pdf.overlay;

import com.pdfformfill.dto.FieldDefinition;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 acceptance: render one field onto a blank one-page PDF, then assert the page text contains the value.
 */
class PdfOverlayRendererTest {

    private final PdfOverlayRenderer renderer = new PdfOverlayRenderer();

    @Test
    void render_draws_text_at_position_and_can_be_read_back() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOnePage();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdfBytes)))) {
            List<FieldDefinition> fields = List.of(
                    new FieldDefinition("A", "string", null, 72d, 700d, 200d, 24d, 1)
            );
            Map<String, Object> fieldData = Map.of("A", "test");
            renderer.render(doc, fields, fieldData, defaultOptions());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            byte[] saved = out.toByteArray();

            try (PDDocument loaded = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(saved)))) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(1);
                String pageText = stripper.getText(loaded);
                assertThat(pageText).contains("test");
            }
        }
    }

    @Test
    void render_empty_fields_does_not_throw() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOnePage();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdfBytes)))) {
            renderer.render(doc, List.of(), Map.of(), defaultOptions());
            renderer.render(doc, null, Map.of("A", "x"), defaultOptions());
        }
    }

    /** Phase 2: long string is shrunk to fit within field width; text still appears. */
    @Test
    void render_long_string_shrinks_to_fit_width() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOnePage();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdfBytes)))) {
            List<FieldDefinition> fields = List.of(
                    new FieldDefinition("B", "string", null, 72d, 650d, 80d, 24d, 1)  // narrow width
            );
            Map<String, Object> fieldData = Map.of("B", "HelloWorldLongText");
            renderer.render(doc, fields, fieldData, defaultOptions());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            byte[] saved = out.toByteArray();

            try (PDDocument loaded = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(saved)))) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(1);
                String pageText = stripper.getText(loaded);
                assertThat(pageText).contains("HelloWorldLongText");
            }
        }
    }

    /** Checkbox/boolean with value true and image path draws image (no exception); false draws nothing. */
    @Test
    void render_checkbox_true_draws_image_when_path_provided() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOnePage();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdfBytes)))) {
            List<FieldDefinition> fields = List.of(
                    new FieldDefinition("chk", "checkbox", null, 72d, 600d, 20d, 20d, 1)
            );
            Map<String, Object> fieldData = Map.of("chk", true);
            renderer.render(doc, fields, fieldData, optionsWithCheckbox("classpath:checked-symbol.png"));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            byte[] saved = out.toByteArray();
            assertThat(saved).isNotEmpty();
        }
    }

    /** Long text with verticalAlign top: font is shrunk to fit width, full text appears (no ellipsis). */
    @Test
    void render_long_text_vertical_align_top_shrinks_to_fit() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOnePage();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdfBytes)))) {
            List<FieldDefinition> fields = List.of(
                    new FieldDefinition("Addr", "string", null, 72d, 580d, 180d, 36d, 1, "top")
            );
            String longText = "123 Sample Street, Sydney NSW 2000, Australia. Unit 5.";
            Map<String, Object> fieldData = Map.of("Addr", longText);
            renderer.render(doc, fields, fieldData, defaultOptions());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            byte[] saved = out.toByteArray();

            try (PDDocument loaded = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(saved)))) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(1);
                String pageText = stripper.getText(loaded);
                assertThat(pageText).contains("123 Sample Street");
                assertThat(pageText).contains("Sydney NSW 2000");
                assertThat(pageText).doesNotContain("...");  // fitted by shrink, not truncated
            }
        }
    }

    /** Phase 2: very long string in very narrow width is truncated with "...". */
    @Test
    void render_very_long_string_truncates_with_ellipsis() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOnePage();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdfBytes)))) {
            List<FieldDefinition> fields = List.of(
                    new FieldDefinition("C", "string", null, 72d, 600d, 30d, 24d, 1)  // very narrow
            );
            Map<String, Object> fieldData = Map.of("C", "ThisIsAVeryLongStringThatWillBeTruncated");
            renderer.render(doc, fields, fieldData, defaultOptions());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            byte[] saved = out.toByteArray();

            try (PDDocument loaded = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(saved)))) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(1);
                String pageText = stripper.getText(loaded);
                assertThat(pageText).contains("...");
                assertThat(pageText).contains("This");  // prefix preserved
            }
        }
    }

    private static OverlayOptions defaultOptions() {
        return new OverlayOptions(null, null, OverlayOptions.DEFAULT_FONT_SIZE, OverlayOptions.DEFAULT_MIN_FONT_SIZE,
                OverlayOptions.DEFAULT_FONT_COLOR_RGB, OverlayOptions.DEFAULT_PADDING_X, OverlayOptions.DEFAULT_PADDING_Y);
    }

    private static OverlayOptions optionsWithCheckbox(String checkboxPath) {
        return new OverlayOptions(null, checkboxPath, OverlayOptions.DEFAULT_FONT_SIZE, OverlayOptions.DEFAULT_MIN_FONT_SIZE,
                OverlayOptions.DEFAULT_FONT_COLOR_RGB, OverlayOptions.DEFAULT_PADDING_X, OverlayOptions.DEFAULT_PADDING_Y);
    }

    private static byte[] createMinimalPdfWithOnePage() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
