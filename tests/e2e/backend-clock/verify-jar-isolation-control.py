"""Structural ZIP controls for the real inspector; these are not production Java artifacts."""

import contextlib
import importlib.util
import io
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

sys.dont_write_bytecode = True
scanner_path = Path(sys.argv.pop(1)) if len(sys.argv) > 1 else Path(__file__).with_name("verify-jar-isolation.py")
spec = importlib.util.spec_from_file_location("jar_isolation_inspector", scanner_path)
if spec is None or spec.loader is None:
    raise SystemExit("Isolation inspector module unavailable")
inspector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inspector)

START_CLASSES = (
    "ru.rutcampustrack.academic.AcademicApplication",
    "ru.rutcampustrack.schedule.ScheduleApplication",
    "ru.rutcampustrack.attendance.AttendanceApplication",
    "ru.rutcampustrack.mobilebff.MobileBffApplication",
)
INITIALIZER = "ru/rutcampustrack/e2e/ClockInitializer.class"


def zipped(entries: dict[str, bytes]) -> bytes:
    output = io.BytesIO()
    with ZipFile(output, "w") as archive:
        for name, content in entries.items():
            archive.writestr(name, content)
    return output.getvalue()


class IsolationInspectorControl(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="rct-jar-isolation-control-")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.fixture = self.directory / "fixture.jar"
        self.fixture.write_bytes(zipped({INITIALIZER: b"structural-control"}))
        self.applications = [self.directory / f"application-{index}.jar" for index in range(4)]
        self.write_applications()

    def write_applications(self, extra: dict[str, bytes] | None = None) -> None:
        for index, (path, start) in enumerate(zip(self.applications, START_CLASSES, strict=True)):
            entries = {
                "META-INF/MANIFEST.MF": f"Start-Class: {start}\n".encode(),
                f"BOOT-INF/classes/{start.replace('.', '/')}.class": b"structural-control",
                "BOOT-INF/lib/ordinary-support.jar": zipped({"ordinary/Support.class": b"structural-control"}),
            }
            if index == 0 and extra:
                entries.update(extra)
            path.write_bytes(zipped(entries))

    def verify(self) -> None:
        with contextlib.redirect_stdout(io.StringIO()):
            inspector.verify(self.fixture, self.applications)

    def test_clean_distinct_structural_applications_pass(self) -> None:
        self.verify()

    def test_outer_fixture_class_is_rejected(self) -> None:
        self.write_applications({f"BOOT-INF/classes/{INITIALIZER}": b"structural-control"})
        with self.assertRaisesRegex(SystemExit, "contaminated"):
            self.verify()

    def test_renamed_nested_dependency_with_fixture_class_is_rejected(self) -> None:
        self.write_applications({"BOOT-INF/lib/educational-support.jar": zipped({INITIALIZER: b"structural-control"})})
        with self.assertRaisesRegex(SystemExit, "contaminated"):
            self.verify()

    def test_deeper_nested_fixture_class_is_rejected(self) -> None:
        nested = zipped({"lib/ordinary-name.jar": zipped({INITIALIZER: b"structural-control"})})
        self.write_applications({"BOOT-INF/lib/ordinary-wrapper.jar": nested})
        with self.assertRaisesRegex(SystemExit, "contaminated"):
            self.verify()

    def test_uninspectable_nested_dependency_fails_closed(self) -> None:
        self.write_applications({"BOOT-INF/lib/uninspectable.jar": b"not-a-zip"})
        with self.assertRaisesRegex(SystemExit, "not an inspectable jar"):
            self.verify()

    def test_duplicate_start_class_is_rejected(self) -> None:
        self.applications[1].write_bytes(self.applications[0].read_bytes())
        with self.assertRaisesRegex(SystemExit, "four distinct"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
