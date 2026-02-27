package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * PDF 表单字段定义文件根结构，与前端导出的 JSON（imported 到后端）一致。
 * 若来自 pdf-tool-spike 等前端：坐标为 viewport 像素（canvas 坐标），需提供 scale（1 PDF point = scale 像素）以便后端换算为 PDF 点。
 * checkboxSymbol/checkboxCheckedImage：可选，勾选态图片路径（classpath:xxx 或文件路径）；空则用配置项默认。
 * fontSize、fontColor、paddingX、paddingY：可选，用于文字 overlay；不传则用后端默认。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldsDefinition(
        List<FieldDefinition> fields,
        Double scale,
        @JsonAlias("checkboxSymbol") String checkboxCheckedImage,
        Integer fontSize,
        String fontColor,
        Double paddingX,
        Double paddingY
) {
    public FieldsDefinition(List<FieldDefinition> fields) {
        this(fields, null, null, null, null, null, null);
    }

    public FieldsDefinition(List<FieldDefinition> fields, Double scale) {
        this(fields, scale, null, null, null, null, null);
    }

    public FieldsDefinition(List<FieldDefinition> fields, Double scale, String checkboxCheckedImage) {
        this(fields, scale, checkboxCheckedImage, null, null, null, null);
    }
}
