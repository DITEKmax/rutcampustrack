"""Inspect actual built artifacts; no service, JVM, credentials or Gradle process is started."""

import json
from io import BytesIO
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile


def require_fixture_absent(archive: ZipFile, depth: int = 0) -> None:
    """Inspect nested classpath dependencies by contents, regardless of artifact filename."""
    if depth > 8:
        raise SystemExit("Production jar nesting exceeds the isolation inspection limit")
    for name in archive.namelist():
        if "ru/rutcampustrack/e2e/" in name or "clock-fixture" in name:
            raise SystemExit("Production Boot jar contaminated by teaching-clock fixture")
        if name.endswith(".jar"):
            try:
                with ZipFile(BytesIO(archive.read(name))) as dependency:
                    require_fixture_absent(dependency, depth + 1)
            except BadZipFile as error:
                raise SystemExit("Production nested dependency is not an inspectable jar") from error


def verify(fixture: Path, applications: list[Path]) -> None:
    if len(applications) != 4 or not fixture.is_file() or not all(p.is_file() for p in applications):
        raise SystemExit("Require the external fixture jar and four built educational Boot jars")
    with ZipFile(fixture) as archive:
        names = archive.namelist()
        if "ru/rutcampustrack/e2e/ClockInitializer.class" not in names:
            raise SystemExit("External fixture initializer class missing")
        if any("ClockFixtureControl" in name for name in names):
            raise SystemExit("Control classes must not be packaged in the fixture jar")
    expected = {
        "ru.rutcampustrack.academic.AcademicApplication",
        "ru.rutcampustrack.schedule.ScheduleApplication",
        "ru.rutcampustrack.attendance.AttendanceApplication",
        "ru.rutcampustrack.mobilebff.MobileBffApplication",
    }
    inspected = set()
    for application in applications:
        with ZipFile(application) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf8")
            start = next(
                (
                    line.removeprefix("Start-Class: ").strip()
                    for line in manifest.splitlines()
                    if line.startswith("Start-Class: ")
                ),
                None,
            )
            if start not in expected or start in inspected:
                raise SystemExit("Require four distinct educational production Boot jars")
            if f"BOOT-INF/classes/{start.replace('.', '/')}.class" not in archive.namelist():
                raise SystemExit("Production Boot jar main class missing")
            inspected.add(start)
            require_fixture_absent(archive)
    print(json.dumps({"externalFixture": "PRESENT", "productionBootJarsInspected": 4, "fixtureAbsent": True}))


if __name__ == "__main__":
    if len(sys.argv) != 6:
        raise SystemExit("Usage: verify-jar-isolation.py fixture.jar academic.jar schedule.jar attendance.jar bff.jar")
    verify(Path(sys.argv[1]), [Path(arg) for arg in sys.argv[2:]])
