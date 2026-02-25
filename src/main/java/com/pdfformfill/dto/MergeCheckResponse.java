package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 阶段 3：仅做解析与校验时的成功响应（暂不生成 PDF）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeCheckResponse(
        boolean success,
        String message,
        Integer templatePages,
        Integer definitionFields
) {
    public static MergeCheckResponse ok(int templatePages, int definitionFields) {
        return new MergeCheckResponse(
                true,
                "Template and definition parsed successfully.",
                templatePages,
                definitionFields
        );
    }
}
