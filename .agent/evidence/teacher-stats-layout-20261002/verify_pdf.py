from pathlib import Path
import hashlib, json, re
from pypdf import PdfReader

root = Path(__file__).resolve().parent
normalize = lambda text: re.sub(r"\s+", " ", text).strip()
results = {}
for scope, count, page_count in (("students", 80, 4), ("groups", 60, 5)):
    pdf = root / "render-r1" / f"teacher-stats-{scope}.pdf"
    pages = [normalize(page.extract_text()) for page in PdfReader(pdf).pages]
    text = " ".join(pages)
    labels = (root / "samples" / f"teacher-stats-{scope}-labels.txt").read_text(encoding="utf-8").splitlines()
    repetitions = 2 if scope == "groups" else 1  # selected group list + original table
    assert len(pages) == page_count and len(labels) == count
    for label in labels:
        assert text.count(normalize(label)) == repetitions, label
        assert any(normalize(label) in page for page in pages), f"split complete label: {label}"
    for percent in ("26.3%", "32.1%", "7.1%", "67.9%"):
        assert text.count(percent) == count - 1, (scope, percent)
    assert text.count("— (0/0)") == 4, scope
    for stamp in ("Типы занятий: Лекция, Лабораторная работа", "Период семестра: 02.02.2026 — 16.08.2026",
                  "Период данных: 09.02.2026 — 31.07.2026", "Учтено пар: 28", "16.08.2026 12:34 UTC"):
        assert stamp in text, (scope, stamp)
    if scope == "students":
        assert "Выбранная группа <80> & полный состав" in text
        assert "Выбранный предмет <длинный> & отдельные типы" in text
    pngs = sorted((root / "render-r1").glob(f"{scope}-page-*.png"))
    assert len(pngs) == page_count
    results[scope] = {"rows": count, "pages": page_count, "eachOriginalLabelExactRepetitions": repetitions,
                      "completeLabelsOnPage": True, "suppliedPercentEachOccurrences": count - 1,
                      "zeroDenominatorCells": 4, "contextPreserved": True, "allPngPages": len(pngs)}
artifacts = {}
for path in sorted(root.rglob("*")):
    if path.is_file() and path.suffix in (".docx", ".pdf", ".png"):
        artifacts[path.relative_to(root).as_posix()] = {"bytes": path.stat().st_size,
                                                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
report = {"source": "22235adb5ca6d3d700c353b91f560143ead08c37", "status": "PASS", "scopes": results, "artifacts": artifacts}
(root / "inspection.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"status": "PASS", "scopes": results}, ensure_ascii=False))
