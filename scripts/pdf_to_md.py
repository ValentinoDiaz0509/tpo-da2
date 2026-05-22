#!/usr/bin/env python3
"""
PDF to Markdown converter.
Handles text-based PDFs, scanned PDFs (via OCR), and tables.

Install deps:
    pip install pdfplumber pypdf pytesseract Pillow pymupdf
    # For OCR: also install tesseract-ocr (system package)
    # sudo apt install tesseract-ocr   |   brew install tesseract
"""

import re
import sys
from pathlib import Path

import pdfplumber
from pypdf import PdfReader


# ── Helpers ──────────────────────────────────────────────────────────────────

def clean_text(text: str) -> str:
    """Remove excessive blank lines and fix common extraction artifacts."""
    text = re.sub(r'\n{3,}', '\n\n', text)       # collapse 3+ newlines → 2
    text = re.sub(r'[ \t]+\n', '\n', text)        # trailing spaces
    text = re.sub(r'\n[ \t]+', '\n', text)        # leading spaces on lines
    return text.strip()


def table_to_markdown(table: list[list]) -> str:
    """Convert a pdfplumber table (list of rows) to a Markdown table."""
    if not table or not table[0]:
        return ""

    # Normalize cells (None → empty string)
    rows = [[str(cell or "").replace("\n", " ").strip() for cell in row] for row in table]

    header, *body = rows
    col_widths = [max(len(cell) for cell in col) for col in zip(*rows)]

    def fmt_row(row):
        return "| " + " | ".join(cell.ljust(w) for cell, w in zip(row, col_widths)) + " |"

    separator = "| " + " | ".join("-" * w for w in col_widths) + " |"
    lines = [fmt_row(header), separator] + [fmt_row(r) for r in body]
    return "\n".join(lines)


def ocr_page(page) -> str:
    """Fallback: rasterize a page and run Tesseract OCR on it."""
    try:
        import pytesseract
        from PIL import Image
        import fitz  # PyMuPDF

        # This function receives a pdfplumber page; get the PDF path from it
        # to open with fitz for rasterization
        raise NotImplementedError  # Handled separately below
    except Exception:
        return ""


def page_is_scanned(text: str) -> bool:
    """Heuristic: if extracted text is very short, assume it's a scanned page."""
    return len(text.strip()) < 50


# ── Core converter ────────────────────────────────────────────────────────────

def pdf_to_markdown(pdf_path: str | Path, ocr_fallback: bool = True) -> str:
    pdf_path = Path(pdf_path)
    md_pages = []

    # Read metadata for a title heading
    reader = PdfReader(pdf_path)
    meta = reader.metadata or {}
    title = meta.get("/Title", pdf_path.stem)
    md_pages.append(f"# {title}\n")

    with pdfplumber.open(pdf_path) as pdf:
        for page_num, page in enumerate(pdf.pages, start=1):
            md_pages.append(f"\n---\n\n## Page {page_num}\n")

            # 1. Extract tables first (they take priority over raw text)
            tables = page.extract_tables()
            table_bboxes = [t.bbox for t in page.find_tables()] if tables else []

            if tables:
                for table in tables:
                    md_table = table_to_markdown(table)
                    if md_table:
                        md_pages.append("\n" + md_table + "\n")

            # 2. Extract text, excluding table regions
            if table_bboxes:
                # Crop out table areas to avoid duplicate content
                remaining = page
                for bbox in table_bboxes:
                    try:
                        remaining = remaining.outside_bbox(bbox)
                    except Exception:
                        pass
                text = remaining.extract_text() or ""
            else:
                text = page.extract_text() or ""

            # 3. OCR fallback for scanned pages
            if page_is_scanned(text) and ocr_fallback:
                text = _ocr_page_fitz(pdf_path, page_num - 1)

            if text:
                md_pages.append(clean_text(text) + "\n")

    return "\n".join(md_pages)


def _ocr_page_fitz(pdf_path: Path, page_index: int) -> str:
    """Rasterize with PyMuPDF, then OCR with Tesseract."""
    try:
        import fitz
        import pytesseract
        from PIL import Image
        import io

        doc = fitz.open(pdf_path)
        page = doc[page_index]
        pix = page.get_pixmap(dpi=200)
        img = Image.open(io.BytesIO(pix.tobytes("png")))
        return pytesseract.image_to_string(img)
    except ImportError as e:
        print(f"[warn] OCR skipped — missing dependency: {e}", file=sys.stderr)
        return ""
    except Exception as e:
        print(f"[warn] OCR failed on page {page_index + 1}: {e}", file=sys.stderr)
        return ""


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Convert a PDF to Markdown")
    parser.add_argument("pdf", help="Path to the input PDF file")
    parser.add_argument("-o", "--output", help="Output .md file (default: same name as PDF)")
    parser.add_argument("--no-ocr", action="store_true", help="Disable OCR fallback for scanned pages")
    args = parser.parse_args()

    pdf_path = Path(args.pdf)
    if not pdf_path.exists():
        print(f"Error: file not found: {pdf_path}", file=sys.stderr)
        sys.exit(1)

    output_path = Path(args.output) if args.output else pdf_path.with_suffix(".md")

    print(f"Converting {pdf_path.name} → {output_path.name} ...")
    markdown = pdf_to_markdown(pdf_path, ocr_fallback=not args.no_ocr)
    output_path.write_text(markdown, encoding="utf-8")
    print(f"Done. {len(markdown):,} characters written to {output_path}")


if __name__ == "__main__":
    main()