# PDF Form Fill Backend

Backend service that merges a PDF form template with a **form fields definition** (JSON from issue-115). It fills the template’s AcroForm fields with data (mock data for now), then saves the filled PDF to a configurable output directory.

**Stack:** Java 17, Spring Boot 3.2, Gradle, Apache PDFBox, Springdoc (Swagger UI).

---

## What it does

- Accepts a **PDF template** (with AcroForm) and a **definition file** (JSON listing field names and types).
- Generates mock values per field type (string, number, date, checkbox, etc.) and fills the form.
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
   - **template**: upload a PDF that has AcroForm fields.
   - **definition**: upload a JSON file in issue-115 format (e.g. `fields` array with `name`, `type`, `description`, `x`, `y`, `width`, `height`, `page`).
4. On success you get `outputPath`; the filled PDF is saved under that path (e.g. under `filled-pdfs/`).
