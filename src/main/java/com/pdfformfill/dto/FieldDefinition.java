package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单条表单字段定义，与导入的 JSON（前端 exported 格式）中 fields 元素一致。
 * 坐标 x, y, width, height 为浮点数；page 为页码整数。
 * verticalAlign 可选："top" | "middle"，默认 middle。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldDefinition(
        String name,
        String type,
        String description,
        Double x,
        Double y,
        Double width,
        Double height,
        Integer page,
        String verticalAlign
) {
    public FieldDefinition(String name, String type, String description,
                           Double x, Double y, Double width, Double height, Integer page) {
        this(name, type, description, x, y, width, height, page, null);
    }
}
