Summary
Build a tool to mark fields in an imported PDF and export all marked fields as JSON.

Requirements
- Allow users to draw a rectangle around a desired field in the PDF.
- For each marked field, allow users to input:
  - name
  - type
  - description
- Save rectangle coordinates and location metadata:
  - x
  - y
  - width
  - height
  - page (page number)
- Export a JSON object containing all marked fields and their information.

Good To Have
Auto-detect likely field rectangles based on PDF layout.
Allow users to adjust auto-detected rectangles and then input field metadata.

Output JSON Example
{
  "fields": [
    {
      "name": "field1_anamef_fdasfads",
      "type": "string",
      "description": "This is the worker's name.",
      "x": "",
      "y": "",
      "width": 111,
      "height": 22,
      "page": 1
    },
    {
      "name": "field2_anamef_fdasfads",
      "type": "number",
      "description": "This is the worker's age.",
      "x": "",
      "y": "",
      "width": 111,
      "height": 22,
      "page": 1
    }
  ]
}