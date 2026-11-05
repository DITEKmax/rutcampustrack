"""Admission of current Attendance Gradle test results without Java source identities."""
import stat


def successful_results(report):
    rows = report.get('cases')
    return (
        report.get('collection') == 'RESULTS'
        and isinstance(rows, list)
        and bool(rows)
        and all(isinstance(row, dict) and row.get('status') == 'PASSED' for row in rows)
        and report.get('summary') == {'PASSED': len(rows), 'FAILED': 0, 'ERROR': 0, 'SKIPPED': 0, 'NOT_EXECUTED': 0}
    )


def current_artifact(path, started_ns):
    if started_ns is None:
        return False
    try:
        info = path.lstat()
    except FileNotFoundError:
        return False
    return stat.S_ISREG(info.st_mode) and info.st_size > 0 and info.st_mtime_ns >= started_ns
