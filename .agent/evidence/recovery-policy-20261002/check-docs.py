"""Check runbook links and syntax only; documented commands are never executed."""
from pathlib import Path
import re
import subprocess

wt = Path(__file__).resolve().parents[3]
doc = wt / "docs/operations/runbooks/backup-restore.md"
text = doc.read_text(encoding="utf-8")
links = re.findall(r"\]\(([^)]+)\)", text)
for link in links:
    if not link.startswith(("https://", "http://", "#")):
        assert (doc.parent / link.split("#")[0]).resolve().exists(), "Missing relative link"
blocks = re.findall(r"```bash\n(.*?)```", text, re.S)
discovery = next(block for block in blocks if "docker inspect --format" in block)
source = Path(__file__).parent / "discovery-syntax.sh"
source.write_text(discovery, encoding="utf-8", newline="\n")
run = subprocess.run([r"C:\Program Files\Git\bin\bash.exe", "-n", str(source)], capture_output=True)
assert run.returncode == 0, "Discovery block syntax failed"
print(f"PASS relative links={len(links)}; discovery bash -n exit={run.returncode}; no documented command executed; runtime N/A")
