# PDF Form Fill Backend

Backend service that merges a PDF form template with a **form fields definition** (JSON from issue-115). It supports **two modes**: if the template has AcroForm fields, it fills them with data (mock data for now); otherwise it **overlays** text at the coordinates given in the definition. Output is saved to a configurable directory.

**Stack:** Java 17, Spring Boot 3.2, Gradle, Apache PDFBox, Springdoc (Swagger UI).

---

## What it does

- Accepts a **PDF template** and a **definition file** (JSON with a `fields` array: `name`, `type`, `description`, `x`, `y`, `width`, `height`, `page`).
- **AcroForm mode:** If the template has AcroForm fields, generates mock values per field type (string, number, date, checkbox, etc.) and fills the form.
- **Overlay mode:** If the template has no AcroForm, draws each field’s value as text at `(x, y, width, height)` on the given `page` (coordinates in PDF points, origin bottom-left).
- **Definition format:** Optional top-level **`scale`** in the JSON: when present and &gt; 0, `x`, `y`, `width`, `height` are treated as **viewport/canvas pixels** (e.g. from [pdf-tool-spike](https://github.com/YBJ0000/pdf-tool)); the backend converts them to PDF points using `scale` (1 PDF point = `scale` pixels) and flips y from top-left-down to PDF bottom-left-up. Omit `scale` or leave it null to use coordinates as PDF points.
- Writes the filled PDF to `pdf.output.dir` (default: `./filled-pdfs`).

---

## Run the app

```bash
./gradlew bootRun
```

Server runs at **http://localhost:8080**. Swagger UI: **http://localhost:8080/swagger-ui.html**.

---

## How to test

**Unit tests**

```bash
./gradlew test
```

**Manual test (Swagger UI)**

1. Start the app with `./gradlew bootRun`.
2. Open http://localhost:8080/swagger-ui.html.
3. Use **POST /api/pdf/merge**:
   - **template**: upload a PDF (with AcroForm for form fill, or any PDF for overlay).
   - **definition**: upload a JSON file with a `fields` array (`name`, `type`, `description`, `x`, `y`, `width`, `height`, `page`). For **overlay** with coordinates from the frontend (e.g. [pdf-tool-spike](https://github.com/YBJ0000/pdf-tool) export), include **`scale`** in the JSON so positions match; without `scale`, coordinates are treated as PDF points.
4. On success you get `outputPath`; the filled PDF is saved under that path (e.g. under `filled-pdfs/`).
