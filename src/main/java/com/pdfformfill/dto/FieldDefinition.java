package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单条表单字段定义，与 issue-115 导出的 JSON 中 fields 元素一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldDefinition(
        String name,
        String type,
        String description,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        Integer page
) {
}
