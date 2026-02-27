package com.pdfformfill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfformfill.dto.FieldDefinition;
import com.pdfformfill.dto.FieldsDefinition;
import com.pdfformfill.dto.MergeResponse;
import com.pdfformfill.pdf.PdfTemplateLoader;
import com.pdfformfill.pdf.overlay.OverlayOptions;
import com.pdfformfill.pdf.overlay.PdfOverlayRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 编排：加载模板 → 解析定义 → 准备 mock 数据 → overlay 渲染（任意 PDF 均按坐标绘制）→ 保存。
 */
@Service
public class PdfFormFillService {

    private static final Logger log = LoggerFactory.getLogger(PdfFormFillService.class);

    private final PdfTemplateLoader pdfTemplateLoader;
    private final ObjectMapper objectMapper;
    private final FieldDataPreparer fieldDataPreparer;
    private final PdfOverlayRenderer pdfOverlayRenderer;

    @Value("${pdf.output.dir:${user.dir}/filled-pdfs}")
    private String outputDir;

    @Value("${pdf.flatten-before-overlay:true}")
    private boolean flattenBeforeOverlay;

    @Value("${pdf.checkbox.checked-image:classpath:checked-symbol.png}")
    private String defaultCheckboxCheckedImage;

    public PdfFormFillService(
            PdfTemplateLoader pdfTemplateLoader,
            ObjectMapper objectMapper,
            FieldDataPreparer fieldDataPreparer,
            PdfOverlayRenderer pdfOverlayRenderer
    ) {
        this.pdfTemplateLoader = pdfTemplateLoader;
        this.objectMapper = objectMapper;
        this.fieldDataPreparer = fieldDataPreparer;
        this.pdfOverlayRenderer = pdfOverlayRenderer;
    }

    /**
     * 接收模板与定义文件，生成填好的 PDF 并保存到 pdf.output.dir，返回保存路径。
     */
    public MergeResponse merge(MultipartFile template, MultipartFile definition) throws IOException {
        try (PDDocument document = pdfTemplateLoader.load(template.getInputStream())) {
            int templatePages = document.getNumberOfPages();
            if (templatePages <= 0) {
                throw new IOException("PDF template has no pages.");
            }

            String definitionJson = new String(definition.getBytes(), StandardCharsets.UTF_8);
            FieldsDefinition fieldsDefinition = objectMapper.readValue(definitionJson, FieldsDefinition.class);
            int definitionFields = fieldsDefinition.fields() != null ? fieldsDefinition.fields().size() : 0;

            Map<String, Object> fieldData = fieldDataPreparer.prepareMockData(fieldsDefinition);

            // Flatten AcroForm so widget appearances (e.g. gray field backgrounds) are merged
            // into the page content stream. Our overlay then draws on top and is no longer covered.
            if (flattenBeforeOverlay) {
                flattenAcroFormIfPresent(document);
            }

            List<FieldDefinition> fields = fieldsDefinition.fields() != null
                    ? fieldsDefinition.fields()
                    : Collections.emptyList();
            OverlayOptions options = OverlayOptions.from(fieldsDefinition, defaultCheckboxCheckedImage);
            pdfOverlayRenderer.render(document, fields, fieldData, options);

            String outputPath = saveToOutputDir(document);
            return MergeResponse.ok(outputPath, templatePages, definitionFields);
        }
    }

    /**
     * If the document has an AcroForm, flatten it so that form field widget appearances
     * (e.g. gray backgrounds) are merged into the page content stream. After that, our
     * overlay text is drawn on top and is no longer covered by annotation layers.
     */
    private void flattenAcroFormIfPresent(PDDocument document) {
        try {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm(null);
            if (acroForm == null) {
                return;
            }
            acroForm.flatten();
            log.debug("AcroForm flattened so overlay text will appear above field backgrounds.");
        } catch (Exception e) {
            log.warn("Could not flatten AcroForm (document may have no form or flatten failed): {}. Overlay will still run.", e.getMessage());
        }
    }

    private String saveToOutputDir(PDDocument document) throws IOException {
        String dirStr = outputDir != null ? outputDir : System.getProperty("user.dir") + "/filled-pdfs";
        Path dir = Paths.get(dirStr);
        Files.createDirectories(dir);
        String filename = "filled-" + UUID.randomUUID() + ".pdf";
        Path target = dir.resolve(filename);
        document.save(target.toFile());
        return target.toAbsolutePath().toString();
    }
}
