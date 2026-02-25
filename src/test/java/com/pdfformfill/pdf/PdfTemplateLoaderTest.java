package com.pdfformfill.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 2 验收：用临时 PDF 调用 loader，断言返回非 null 的 PDDocument 且 getNumberOfPages() > 0。
 */
class PdfTemplateLoaderTest {

    private final PdfTemplateLoader loader = new PdfTemplateLoader();

    @Test
    void load_returns_non_null_PDDocument_with_pages() throws IOException {
        byte[] pdfBytes = createMinimalPdfWithOnePage();
        try (InputStream in = new ByteArrayInputStream(pdfBytes)) {
            PDDocument document = loader.load(in);
            try {
                assertThat(document).isNotNull();
                assertThat(document.getNumberOfPages()).isGreaterThan(0);
            } finally {
                document.close();
            }
        }
    }

    private static byte[] createMinimalPdfWithOnePage() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
