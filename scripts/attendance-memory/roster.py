"""Current full-task source roster, without a pinned number of JUnit invocations."""
import importlib.util


def expected_roster(root):
    spec = importlib.util.spec_from_file_location('attendance_outcomes', root / 'scripts/collect-ci-test-outcomes.py')
    collector = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(collector)
    registry = collector.source_registry(root, collector.MODULES['attendance-service'], reject_duplicates=True)
    result = {'test': set(), 'integrationTest': set()}
    for classname, source in registry.items():
        # Gradle selects the top-level *IT class; its nested tests belong to the same task.
        task = 'integrationTest' if classname.split('$')[0].endswith('IT') else 'test'
        result[task].update((classname, method) for method in source['methods'])
    if not all(result.values()):
        raise ValueError('EMPTY_SOURCE_ROSTER')
    return result


def confirm_roster(report, expected):
    """Every selected source method must execute; invocations can vary with its arguments.

    The runner invokes both whole tasks without --tests filters. Native exit zero is
    additionally required by arm acceptance; a source method cannot disappear behind
    an unchanged total, a skipped method, an unknown identity or a partial XML suite.
    """
    rows = report.get('cases', [])
    summary = report.get('summary', {})
    identities = {(row.get('classname'), row.get('method')) for row in rows}
    return (
        bool(expected)
        and report.get('collection') == 'RESULTS'
        and identities == expected
        and all(row.get('identity') == 'SOURCE_METHOD' and row.get('status') == 'PASSED' for row in rows)
        and summary == {'PASSED': len(rows), 'FAILED': 0, 'ERROR': 0, 'SKIPPED': 0, 'NOT_EXECUTED': 0}
    )
