"""Parse tracked PowerShell bytes with PowerShell7; never executes that source."""
from pathlib import Path
import shutil
import subprocess

def main():
    binary=shutil.which('pwsh') or shutil.which('pwsh.exe')
    if binary is None:raise SystemExit('PowerShell7 pwsh or pwsh.exe is required for parser-only validation')
    command="if ($PSVersionTable.PSVersion.Major -ne 7) { exit 3 }; $t=$null; $e=$null; [System.Management.Automation.Language.Parser]::ParseInput([Console]::In.ReadToEnd(),[ref]$t,[ref]$e) | Out-Null; if ($e.Count -gt 0) { exit 2 }"
    result=subprocess.run([binary,'-NoLogo','-NoProfile','-NonInteractive','-Command',command],
                          input=Path('scripts/local-stand.ps1').read_text(encoding='utf-8'),text=True,timeout=30)
    raise SystemExit(result.returncode)

if __name__=='__main__':main()
