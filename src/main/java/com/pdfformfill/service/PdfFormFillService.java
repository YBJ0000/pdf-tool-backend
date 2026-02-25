package com.pdfformfill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfformfill.dto.FieldsDefinition;
import com.pdfformfill.dto.MergeResponse;
import com.pdfformfill.pdf.PdfFormFiller;
import com.pdfformfill.pdf.PdfTemplateLoader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 编排：加载模板 → 解析定义 → 准备 mock 数据 → 合并填表 → 保存到配置目录。
 */
@Service
public class PdfFormFillService {

    private final PdfTemplateLoader pdfTemplateLoader;
    private final ObjectMapper objectMapper;
    private final FieldDataPreparer fieldDataPreparer;
    private final PdfFormFiller pdfFormFiller;

    @Value("${pdf.output.dir}")
    private String outputDir;

    public PdfFormFillService(
            PdfTemplateLoader pdfTemplateLoader,
            ObjectMapper objectMapper,
            FieldDataPreparer fieldDataPreparer,
            PdfFormFiller pdfFormFiller
    ) {
        this.pdfTemplateLoader = pdfTemplateLoader;
        this.objectMapper = objectMapper;
        this.fieldDataPreparer = fieldDataPreparer;
        this.pdfFormFiller = pdfFormFiller;
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
            pdfFormFiller.fill(document, fieldData);

            String outputPath = saveToOutputDir(document);
            return MergeResponse.ok(outputPath, templatePages, definitionFields);
        }
    }

    private String saveToOutputDir(PDDocument document) throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        String filename = "filled-" + UUID.randomUUID() + ".pdf";
        Path target = dir.resolve(filename);
        document.save(target.toFile());
        return target.toAbsolutePath().toString();
    }
}
