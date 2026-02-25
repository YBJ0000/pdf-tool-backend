package com.pdfformfill.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阶段 5 验收：无 AcroForm 时抛异常；有 AcroForm 时填充并可读回值。
 */
class PdfFormFillerTest {

    private final PdfTemplateLoader loader = new PdfTemplateLoader();
    private final PdfFormFiller filler = new PdfFormFiller();

    @Test
    void fill_throws_when_pdf_has_no_acro_form() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithNoForm();
        try (InputStream in = new ByteArrayInputStream(pdfBytes);
             PDDocument doc = loader.load(in)) {
            assertThatThrownBy(() -> filler.fill(doc, Map.of("any", "value")))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("该 PDF 不是表单");
        }
    }

    @Test
    void fill_sets_text_field_value_and_can_be_read_back() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOneTextField();
        try (InputStream in = new ByteArrayInputStream(pdfBytes);
             PDDocument doc = loader.load(in)) {
            filler.fill(doc, Map.of("TestField", "hello"));
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            assertThat(acroForm).isNotNull();
            assertThat(acroForm.getField("TestField").getValueAsString()).isEqualTo("hello");
        }
    }

    /** 无表单的最小 PDF（阶段 2 同款）. */
    private static byte[] createMinimalPdfWithNoForm() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 带一个文本域 "TestField" 的最小 PDF. */
    private static byte[] createMinimalPdfWithOneTextField() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            form.setDefaultResources(new org.apache.pdfbox.pdmodel.PDResources());
            form.getDefaultResources().put(org.apache.pdfbox.cos.COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));

            PDTextField textField = new PDTextField(form);
            textField.setPartialName("TestField");
            textField.setDefaultAppearance("/Helv 12 Tf 0 0 0 rg");
            form.getFields().add(textField);

            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget widget = textField.getWidgets().get(0);
            widget.setRectangle(new PDRectangle(50, 700, 200, 25));
            widget.setPage(page);
            page.getAnnotations().add(widget);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
