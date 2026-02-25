package com.pdfformfill.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfformfill.dto.FieldsDefinition;
import com.pdfformfill.dto.MergeCheckResponse;
import com.pdfformfill.pdf.PdfTemplateLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * REST 接口：接收 PDF 模板 + 表单字段定义 JSON，当前仅做解析与校验（阶段 3）。
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfMergeController {

    private final PdfTemplateLoader pdfTemplateLoader;
    private final ObjectMapper objectMapper;

    public PdfMergeController(PdfTemplateLoader pdfTemplateLoader, ObjectMapper objectMapper) {
        this.pdfTemplateLoader = pdfTemplateLoader;
        this.objectMapper = objectMapper;
    }

    @Operation(
            summary = "校验模板与定义文件",
            description = "上传 PDF 模板与 issue-115 格式的字段定义 JSON，仅解析并校验，暂不生成填好的 PDF。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解析成功"),
            @ApiResponse(responseCode = "400", description = "请求参数无效（缺少文件或格式错误）"),
            @ApiResponse(responseCode = "415", description = "不支持的媒体类型或内容无法解析")
    })
    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> merge(
            @RequestParam("template") MultipartFile template,
            @RequestParam("definition") MultipartFile definition
    ) {
        if (template.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorBody("Missing or empty template file."));
        }
        if (definition.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorBody("Missing or empty definition file."));
        }

        PDDocument document = null;
        try {
            document = pdfTemplateLoader.load(template.getInputStream());
            int templatePages = document.getNumberOfPages();
            if (templatePages <= 0) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body(new ErrorBody("PDF template has no pages."));
            }

            String definitionJson = new String(definition.getBytes(), StandardCharsets.UTF_8);
            FieldsDefinition fieldsDefinition = objectMapper.readValue(definitionJson, FieldsDefinition.class);
            int definitionFields = fieldsDefinition.fields() != null ? fieldsDefinition.fields().size() : 0;

            return ResponseEntity.ok(MergeCheckResponse.ok(templatePages, definitionFields));
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorBody("Invalid definition JSON: " + (e.getMessage() != null ? e.getMessage() : "parse error")));
        } catch (IOException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(new ErrorBody("Invalid PDF template or stream error: " + message));
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Schema(description = "错误响应体")
    public record ErrorBody(String message) {}
}
