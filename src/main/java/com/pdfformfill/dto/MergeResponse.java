package com.pdfformfill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 阶段 6：合并并保存成功后的响应，包含输出文件路径。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeResponse(
        boolean success,
        String message,
        String outputPath,
        Integer templatePages,
        Integer definitionFields
) {
    public static MergeResponse ok(String outputPath, int templatePages, int definitionFields) {
        return new MergeResponse(
                true,
                "Filled PDF saved successfully.",
                outputPath,
                templatePages,
                definitionFields
        );
    }
}
