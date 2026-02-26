package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * PDF 表单字段定义文件根结构，与 issue-115 导出的 fields.json 一致。
 * 若来自 pdf-tool-spike 等前端：坐标为 viewport 像素（canvas 坐标），需提供 scale（1 PDF point = scale 像素）以便后端换算为 PDF 点。
 * checkboxCheckedImage：可选，勾选态图片路径（classpath:xxx 或文件路径），用于渲染 type=checkbox/boolean 且值为 true 的字段；不设则用配置项默认。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldsDefinition(
        List<FieldDefinition> fields,
        Double scale,
        String checkboxCheckedImage
) {
    public FieldsDefinition(List<FieldDefinition> fields) {
        this(fields, null, null);
    }

    public FieldsDefinition(List<FieldDefinition> fields, Double scale) {
        this(fields, scale, null);
    }
}
