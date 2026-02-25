package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * PDF 表单字段定义文件根结构，与 issue-115 导出的 fields.json 一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldsDefinition(
        List<FieldDefinition> fields
) {
}
