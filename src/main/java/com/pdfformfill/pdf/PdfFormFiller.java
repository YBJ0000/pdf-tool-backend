package com.pdfformfill.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 将字段数据填入 PDF 的 AcroForm，按 name 匹配；不修改原始流，仅修改传入的 PDDocument。
 */
@Component
public class PdfFormFiller {

    private static final Logger log = LoggerFactory.getLogger(PdfFormFiller.class);

    /**
     * 用 fieldData（name → value）填充文档中的表单。若 PDF 无 AcroForm 则抛出异常。
     *
     * @param document  已加载的 PDF，将被原地修改
     * @param fieldData 字段名 → 值（String / Number / Boolean）
     * @throws IOException 若该 PDF 不是表单（AcroForm 为 null）或填表失败
     */
    public void fill(PDDocument document, Map<String, Object> fieldData) throws IOException {
        if (document == null) {
            throw new IOException("PDDocument is null");
        }
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
        if (acroForm == null) {
            throw new IOException("该 PDF 不是表单");
        }
        if (fieldData == null || fieldData.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : fieldData.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            PDField field = acroForm.getField(name);
            if (field == null) {
                log.warn("No AcroForm field found for name: {}", name);
                continue;
            }
            setFieldValue(field, value);
        }
    }

    private void setFieldValue(PDField field, Object value) throws IOException {
        if (field instanceof PDCheckBox) {
            boolean checked = value instanceof Boolean && (Boolean) value;
            if (checked) {
                ((PDCheckBox) field).check();
            } else {
                ((PDCheckBox) field).unCheck();
            }
        } else {
            String str = value != null ? value.toString() : "";
            field.setValue(str);
        }
    }
}
