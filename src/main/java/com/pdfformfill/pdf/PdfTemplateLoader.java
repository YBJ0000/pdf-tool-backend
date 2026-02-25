package com.pdfformfill.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;

/**
 * 加载 PDF 模板为 {@link PDDocument}，不填表、仅加载。
 * 调用方负责关闭返回的 PDDocument（或使用 try-with-resources）。
 */
public class PdfTemplateLoader {

    /**
     * 从输入流加载 PDF，返回的文档由调用方负责关闭。
     *
     * @param inputStream PDF 数据流，由调用方管理生命周期
     * @return 非 null 的 PDDocument
     * @throws IOException 读取或解析失败时抛出
     */
    public PDDocument load(InputStream inputStream) throws IOException {
        return Loader.loadPDF(new RandomAccessReadBuffer(inputStream));
    }
}
