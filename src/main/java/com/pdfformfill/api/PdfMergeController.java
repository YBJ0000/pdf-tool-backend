package com.pdfformfill.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pdfformfill.dto.MergeResponse;
import com.pdfformfill.service.PdfFormFillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST 接口：接收 PDF 模板 + 字段定义 JSON，按定义在坐标位置 overlay 绘制字段值并保存。
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfMergeController {

    private final PdfFormFillService pdfFormFillService;

    public PdfMergeController(PdfFormFillService pdfFormFillService) {
        this.pdfFormFillService = pdfFormFillService;
    }

    @Operation(
            summary = "合并并保存填好的 PDF",
            description = "上传任意 PDF 模板与 issue-115 格式的字段定义 JSON，按定义生成 mock 数据并在 (x,y,width,height,page) 位置 overlay 绘制文本，保存到 pdf.output.dir，返回输出文件路径。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "保存成功，返回 outputPath"),
            @ApiResponse(responseCode = "400", description = "请求参数无效（缺少文件或 definition 非合法 JSON）"),
            @ApiResponse(responseCode = "500", description = "保存失败（如模板无效、目录无写权限）")
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

        try {
            MergeResponse result = pdfFormFillService.merge(template, definition);
            return ResponseEntity.ok(result);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorBody("Invalid definition JSON: " + (e.getMessage() != null ? e.getMessage() : "parse error")));
        } catch (IOException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorBody("Failed to generate or save PDF: " + message));
        }
    }

    @Schema(description = "错误响应体")
    public record ErrorBody(String message) {}
}
