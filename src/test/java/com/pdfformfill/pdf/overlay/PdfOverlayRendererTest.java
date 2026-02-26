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
            renderer.render(doc, fields, fieldData);

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
            renderer.render(doc, List.of(), Map.of());
            renderer.render(doc, null, Map.of("A", "x"));
        }
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
