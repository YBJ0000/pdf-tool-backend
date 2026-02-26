package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * PDF 表单字段定义文件根结构，与 issue-115 导出的 fields.json 一致。
 * 若来自 pdf-tool-spike 等前端：坐标为 viewport 像素（canvas 坐标），需提供 scale（1 PDF point = scale 像素）以便后端换算为 PDF 点。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldsDefinition(
        List<FieldDefinition> fields,
        Double scale
) {
    public FieldsDefinition(List<FieldDefinition> fields) {
        this(fields, null);
    }
}
