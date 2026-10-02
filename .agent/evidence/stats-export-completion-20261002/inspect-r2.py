import hashlib
import json
import re
import zipfile
from pathlib import Path
from pypdf import PdfReader

root = Path(__file__).parent
render = root / "render-r2"
reader = PdfReader(render / "large-selected-stats.pdf")
page_text = [page.extract_text() for page in reader.pages]
text = "\n".join(page_text)
counts = [len(re.findall(rf"Студент-{index:03d}(?!\d)", text)) for index in range(1, 121)]
assert counts == [2] * 120, "PDF must retain every supplied row in both tables"
context = ["Синтетическая группа <120> & полный состав", "02.02.2026", "16.08.2026", "lecture, lab",
           "Фильтры: ФИО: содержит «<Синтетический>»", "от 0 (включительно)",
           "Сортировка: 1. ФИО: по возрастанию; 2. Процент «+»: по убыванию"]
assert all(value in page_text[0] for value in context), "PDF selected context changed"
assert "26.3%" in text and "— (0/0)" in text, "Supplied/no-data metric representation changed"
pages = sorted(render.glob("page-*.png"), key=lambda path: int(path.stem.split("-")[-1]))
assert len(pages) == len(reader.pages) == 9
archive = root / "large-selected-stats-r2-pages.zip"
assert not archive.exists(), "No overwrite of prior evidence"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
    for index, page in enumerate(pages, 1):
        output.write(page, f"page-{index:04d}.png")
with zipfile.ZipFile(archive) as output:
    assert output.testzip() is None
    assert output.namelist() == [f"page-{index:04d}.png" for index in range(1, 10)]
    assert all(output.read(name) == page.read_bytes() for name, page in zip(output.namelist(), pages))
files = [root / f"large-selected-stats-r2.{ext}" for ext in ("docx", "html", "xlsx")]
files += [render / "large-selected-stats.pdf", archive]
result = {"source": "4b35683bde9d595af22904c53ec933b10a9ec3a1", "pdfPages": len(reader.pages),
          "students": 120, "rowOccurrencesPerStudent": 2, "totalStudentRowsInPdf": sum(counts),
          "selectedContextPresent": True, "suppliedPercentagePreserved": True, "noDataDashPresent": True,
          "pngPages": len(pages), "archiveEntryCount": 9, "crcPass": True, "archiveMatchesEveryOriginalPng": True,
          "archiveEvidence": "Offline derivative of actual Poppler pages; unchanged service ZIP behavior inherited, no new HTTP claim",
          "files": [{"path": str(path.relative_to(root)), "bytes": path.stat().st_size,
                     "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in files]}
(root / "inspection-r2.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
