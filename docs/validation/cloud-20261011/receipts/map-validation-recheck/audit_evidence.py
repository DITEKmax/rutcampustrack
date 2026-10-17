"""Read/hash/XML/chunk audit only. Does not inflate images or execute product code."""
from pathlib import Path
import datetime
import hashlib
import json
import re
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile
import zlib

OUT = Path(__file__).parent
AUTHOR = Path('/workspace/scratch/rct-validation/file_boundaries_developer')
WT = Path('/workspace/rct-worktrees/file-boundaries')
DOC = Path('/workspace/rutcampustrack/docs/validation/cloud-20261011')
PRIOR = DOC / 'receipts/map-validation-review'
OLD = 'e68989d29e65008dbbeba7b8df1b80bcb070a538'
NEW = 'c73ef351d1a1b794e600bcdec2e8f8b282c2e558'
BASE = 'e093eb988785ed48f26c4f3a561f168ed4d96401'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def write(name, obj):
    (OUT / name).write_text(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')

def read(name):
    return json.loads((AUTHOR / name).read_text())

def git(*args):
    return subprocess.check_output(['git', '--no-optional-locks', '-C', str(WT), *args])

OUT.mkdir(parents=True, exist_ok=True)
assert git('rev-parse', 'HEAD').decode().strip() == NEW
assert not git('status', '--porcelain=v1')
inv = read('mvr-inventory.json')['files']
pre = read('green-mvr-06-prelaunch.json')
prep = read('mvr-correction-prep.json')
rows = []
for item in inv:
    p = item['path']
    b = (WT / p).read_bytes()
    blob = git('show', NEW + ':' + p)
    h = digest(b)
    assert b == blob and h == item['sha256'] and len(b) == item['bytes']
    changed = p in pre['hashes']
    if changed:
        assert h == pre['hashes'][p] == prep['hashes'][p]
    else:
        assert b == git('show', OLD + ':' + p)
    dst = OUT / 'source' / p
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(b)
    rows.append(dict(item, git_blob_equal=True, green_prelaunch_equal=changed,
                     correction_prep_equal=changed, unchanged_since_old=not changed))
assert len(pre['hashes']) == 3
redpre = read('red-mvr-05-prelaunch.json')
product = next(p for p in pre['hashes'] if '/main/' in p)
fixture = next(p for p in pre['hashes'] if p.endswith('CampusMapFileFixture.java'))
assert digest(git('show', OLD + ':' + product)) == redpre['hashes'][product]
assert redpre['hashes'][fixture] == pre['hashes'][fixture]
write('source-equivalence.json', {'frozen': NEW, 'old': OLD, 'base': BASE,
    'current_clean': True, 'files': rows,
    'correction_diff': git('diff', '--name-status', OLD, NEW).decode().splitlines(),
    'cumulative_diff': git('diff', '--name-status', BASE, NEW).decode().splitlines(),
    'red_product_exact_old_git': True, 'red_fixture_exact_final_git': True,
    'red_test_source': 'Uncommitted test WIP hash retained by original prelaunch; not falsely equated with final test blob',
    'runtime_relation': 'Green ran uncommitted correction on OLD; three exact hashes bridge to NEW, no claim that NEW was HEAD during run'})

prior_manifest = json.loads((PRIOR / 'sha256-manifest.json').read_text())
prior_rows = []
with zipfile.ZipFile(PRIOR / 'immutable-original-receipts.zip') as archive:
    names = archive.namelist()
    assert len(names) == 40 and len(prior_manifest['files']) == 39
    for item in prior_manifest['files']:
        b = (PRIOR / item['path']).read_bytes()
        assert digest(b) == item['sha256'] and len(b) == item['bytes']
        assert archive.read(item['path']) == b
        prior_rows.append({'path': item['path'], 'sha256': digest(b), 'manifest_equal': True, 'archive_equal': True})
write('prior-immutable-review-audit.json', {'previous_verdict': 'CHANGES_REQUESTED',
    'prior_frozen_source': OLD, '39_manifest_files_equal': True,
    'archive_entries': 40, 'files': prior_rows,
    'archive_sha256': digest((PRIOR / 'immutable-original-receipts.zip').read_bytes()),
    'unchanged_original_bytes': True})

originals = []
selected = ['red-mvr-05-prelaunch.json', 'red-mvr-05-terminal.json', 'red-mvr-05-summary.json',
    'red-mvr-05.log', 'green-mvr-06-prelaunch.json', 'green-mvr-06-terminal.json',
    'green-mvr-06-summary.json', 'green-mvr-06.log', 'mvr-inventory.json',
    'mvr-correction-prep.json', 'final-mvr-receipt.json', 'run-map-check.py']
selected += [str(p.relative_to(AUTHOR)) for folder in ['red-mvr-05-xml',
    'green-mvr-06-test-xml', 'green-mvr-06-integrationTest-xml', 'red-mvr-05-artifacts']
    for p in sorted((AUTHOR / folder).glob('*')) if p.is_file()]
for name in selected:
    p = AUTHOR / name
    assert not p.is_symlink()
    b = p.read_bytes()
    dst = OUT / 'original-evidence' / name
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(b)
    assert dst.read_bytes() == b
    originals.append({'path': str(dst.relative_to(OUT)), 'source': str(p), 'bytes': len(b),
                      'sha256': digest(b), 'transformation': 'byte equal'})
write('original-byte-index.json', {'files': originals, 'original_count': len(originals),
    'red_failures_preserved': True, 'no_log_normalization': True})

xml_audit = []
red_diagnostics = []
for name in selected:
    if not name.endswith('.xml'):
        continue
    root = ET.parse(AUTHOR / name).getroot()
    cases = root.findall('testcase')
    observed = {'tests': len(cases), 'failures': sum(c.find('failure') is not None for c in cases),
        'errors': sum(c.find('error') is not None for c in cases),
        'skipped': sum(c.find('skipped') is not None for c in cases)}
    declared = {k: int(root.get(k, '0')) for k in observed}
    assert observed == declared and observed['errors'] == observed['skipped'] == 0
    assert len(root.findall('properties/property')) == 0
    case_details = []
    for c in cases:
        f = c.find('failure')
        if f is not None:
            assert 'AssertionFailedError' in f.get('type', '')
            assert 'expected: 400' in f.get('message', '') and 'but was: 201' in f.get('message', '')
        case_details.append({'name': c.get('name'), 'failed': f is not None,
            'failure_message': f.get('message') if f is not None else None})
    if name.startswith('red-'):
        assert observed == {'tests': 17, 'failures': 14, 'errors': 0, 'skipped': 0}
        for line in root.findtext('system-out', '').splitlines():
            m = re.fullmatch(r'Unexpected admitted file: status=201 servedStatus=200 byteEquality=true format=(png|svg) bytes=(\d+) sha256=([0-9a-f]{64}) storedAssets=1', line)
            if m:
                red_diagnostics.append({'format': m[1], 'bytes': int(m[2]), 'sha256': m[3],
                    'upload': 201, 'served': 200, 'stored_assets': 1, 'byte_equality': True})
    xml_audit.append({'path': name, 'suite': root.get('name'), 'declared': declared,
        'observed': observed, 'count_match': True, 'properties': 0, 'cases': case_details})
assert len(xml_audit) == 11 and len(red_diagnostics) == 14
green_products = [r for r in xml_audit if r['path'].startswith('green-') and '.map.' in r['suite']]
assert sorted(r['observed']['tests'] for r in green_products) == [2, 4, 37]
write('original-xml-audit.json', {'all_11_count_matches': True, 'all_zero_error_skip': True,
    'suites': xml_audit, 'green_product_suites': green_products,
    'counts_not_unique_scenario_metric': True})

def budget(width, height, depth, color, interlace):
    samples = {0: 1, 3: 1, 2: 3, 4: 2, 6: 4}[color]
    passes = [(0,0,8,8),(4,0,8,8),(0,4,4,8),(2,0,4,4),(0,2,2,4),(1,0,2,2),(0,1,1,2)] if interlace else [(0,0,1,1)]
    total_bytes = total_rows = 0
    pass_rows = []
    for x,y,dx,dy in passes:
        # Independently count coordinates in range(start, bound, step), not the Java remainder algorithm.
        cols = len(range(x, width, dx))
        rows = len(range(y, height, dy))
        if cols and rows:
            scanline = 1 + (cols * samples * depth + 7) // 8
            total_bytes += rows * scanline
            total_rows += rows
            pass_rows.append({'columns': cols, 'rows': rows, 'scanline_bytes': scanline})
    return {'decoded_bytes': total_bytes, 'pass_rows': total_rows, 'passes': pass_rows,
        'accepted_budget': total_bytes <= 134217728 and total_rows <= 1000000}

artifacts = []
for d in red_diagnostics:
    p = AUTHOR / 'red-mvr-05-artifacts' / (d['sha256'] + '.' + d['format'])
    b = p.read_bytes()
    assert digest(b) == d['sha256'] and len(b) == d['bytes']
    row = dict(d, artifact=str(p), actual_hash_equal=True, actual_length_equal=True)
    if d['format'] == 'png':
        assert b[:8] == b'\x89PNG\r\n\x1a\n'
        offset = 8
        chunks = []
        while offset < len(b):
            length = struct.unpack_from('>I', b, offset)[0]
            assert offset + length + 12 <= len(b)
            kind = b[offset+4:offset+8]
            value = b[offset+8:offset+8+length]
            expected = struct.unpack_from('>I', b, offset+8+length)[0]
            assert zlib.crc32(kind + value) == expected
            chunks.append(kind.decode('ascii'))
            if kind == b'IHDR':
                w,h,depth,color,compression,filtering,interlace = struct.unpack('>IIBBBBB', value)
                row['header'] = {'width':w,'height':h,'depth':depth,'color':color,'interlace':interlace}
                row['budget'] = budget(w,h,depth,color,interlace)
                assert not row['budget']['accepted_budget']
            offset += length + 12
        assert chunks == ['IHDR', 'IDAT', 'IEND']
        row['chunks_crc_valid'] = True
        row['image_inflation'] = 'NOT_RUN'
    else:
        row['xml_well_formed'] = ET.fromstring(b).tag == '{http://www.w3.org/2000/svg}svg'
        assert row['xml_well_formed']
    artifacts.append(row)
assert len({r['sha256'] for r in artifacts}) == 14
write('served-artifact-audit.json', {'14_unique_exact_artifacts': True,
    'oracle': 'XML diagnostics and unchanged test helper: actual 201, real PG one asset, GET200 exact input bytes',
    'artifacts': artifacts, 'no_inflation': True})

# Parse the bounded Java literals and rebuild SVG bytes only, never compile or execute Java.
test_path = next(p for p in pre['hashes'] if p.endswith('CampusMapFileBoundaryIT.java'))
test_source = (WT / test_path).read_text()
block = test_source.split('void cssImageSetCannotPublishQuotedExternalResource')[0].rsplit('@ValueSource(strings = {', 1)[1]
css_values = [json.loads(v) for v in re.findall(r'("(?:[^"\\]|\\.)*")', block)]
assert len(css_values) == 7
by_hash = {a['sha256']: a for a in artifacts}
svg_bridges = []
for value in css_values:
    b = ("<svg xmlns='http://www.w3.org/2000/svg' width='2' height='2' style='"
         + value.replace("'", '&apos;') + "'><style>rect { " + value
         + " }</style><rect width='2' height='2'/></svg>").encode()
    h = digest(b)
    assert h in by_hash
    svg_bridges.append({'kind': 'css', 'final_source_value': value, 'sha256': h,
        'exact_final_input_equal_red_served': True})
svg_root = "<svg xmlns='http://www.w3.org/2000/svg' width='2' height='2'><rect width='2' height='2'/></svg>"
for position in ['before-root', 'after-root', 'relative', 'entity-reference']:
    target = '/rct-file-marker.css' if position == 'relative' else ('&#104;ttps://example.invalid/rct-file-marker.css'
        if position == 'entity-reference' else 'https://example.invalid/rct-file-marker.css')
    pi = "<?xml-stylesheet type='text/css' href='" + target + "'?>"
    b = ("<?xml version='1.0' encoding='UTF-8'?>" + (svg_root + pi if position == 'after-root' else pi + svg_root)).encode()
    h = digest(b)
    assert h in by_hash
    svg_bridges.append({'kind': 'pi', 'position': position, 'sha256': h,
        'exact_final_input_equal_red_served': True})
write('negative-input-source-bridge.json', {'11_final_negative_svg_bytes_equal_old_served': True,
    'svg_inputs': svg_bridges, 'png_inputs': 'Exact red/final fixture hash equality and independently checked IHDR/CRC/work counts; no generation/inflation replay'})
controls = {name: budget(*values) for name, values in {
    'row_limit': (1,1000000,1,0,0), 'byte_limit': (134217727,1,8,0,0),
    'row_over': (1,1000001,1,0,0), 'byte_over': (134217728,1,8,0,0),
    'adam7_over': (2,666667,1,0,1), 'maximal_rgba16': (2147483647,2147483647,16,6,0),
    'wide_existing': (100000,1,8,0,0), 'small_gray': (7,5,1,0,0),
    'small_adam7_gray': (7,5,1,0,1)}.items()}
assert controls['row_limit']['pass_rows'] == 1000000
assert controls['byte_limit']['decoded_bytes'] == 134217728
assert controls['adam7_over']['pass_rows'] == 1000001
assert controls['maximal_rgba16']['decoded_bytes'] > 2**63 - 1
assert controls['maximal_rgba16']['passes'][0]['scanline_bytes'] < 2**63 - 1
write('png-budget-audit.json', {'independent_arithmetic': 'Python integers and range cardinality; no image generation/inflation',
    'controls': controls, 'proof': 'Validated positive int IHDR, samples<=4/depth<=16 keeps per-row long safe; compare rows against remainingBytes/scanlineBytes before rows*scanlineBytes, only subtract admitted bounded products',
    'before_inflater_lines': [528,543], 'bounded_stream_lines': [544,546],
    'compressed_upload_limit_lines': [428,438]})

logs = []
for run, code in [('red-mvr-05',1),('green-mvr-06',0)]:
    terminal = read(run + '-terminal.json')
    launch = read(run + '-prelaunch.json')
    summary = read(run + '-summary.json')
    assert terminal['status'] == 'TERMINAL' and terminal['exit_code'] == summary['exit_code'] == code
    assert terminal['argv'] == launch['argv'] and terminal['hashes'] == launch['hashes']
    lines = (AUTHOR / (run + '.log')).read_text().splitlines()
    markers = [{'line':n,'text':l} for n,l in enumerate(lines,1) if 'BUILD SUCCESSFUL' in l or 'BUILD FAILED' in l or 'tests completed' in l or l.startswith('> Task')]
    assert sum('BUILD SUCCESSFUL' in l for l in lines) == (code == 0)
    assert sum('BUILD FAILED' in l for l in lines) == (code == 1)
    logs.append({'run':run,'author_session':summary['session'],'exit':code,'terminal_equal_prelaunch':True,
        'argv':launch['argv'],'head_during_execution':launch['head'], 'markers':markers,
        'process_exit_cleanup_scope':'Author terminal/cleanup receipts reviewed, independent runtime NOT_RUN'})
write('original-log-audit.json', {'runs':logs, 'no_replay': True})

inputs = [DOC/'packet-map-validation-recheck.md', DOC/'TASK-CONTRACT.md', DOC/'TASK-CONTRACT.original.txt',
    DOC/'CURRENT.md', DOC/'decisions.md', DOC/'questions.md', DOC/'resources.md',
    Path('/workspace/rutcampustrack/docs/product/decisions/2026-10-11-png-validation-resource-budget.md'),
    Path('/workspace/rutcampustrack/docs/product/decisions/2026-10-11-map-legend-deferred.md'),
    Path('/workspace/rutcampustrack/docs/wireframes/admin/135-admin-map.md'),
    Path('/workspace/rutcampustrack/.agent/orchestration-v2/RULES.md'),
    Path('/workspace/rutcampustrack/.agent/orchestration-v2/CURRENT.md')]
write('input-hash-manifest.json', {'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'files':[{'path':str(p),'bytes':p.stat().st_size,'sha256':digest(p.read_bytes())} for p in inputs],
    'requested_model':'gpt-6.1-sol','requested_effort':'xhigh','actual_metadata':'UNKNOWN',
    'independent_runtime':'NOT_RUN','product_writes':'NONE','children':'NONE'})
print(json.dumps({'source_files':len(rows),'correction_files':len(pre['hashes']),
    'prior_immutable_files':len(prior_rows),'current_original_evidence':len(originals),
    'xml_files':len(xml_audit),'red_cases':17,'red_policy_failures':14,'red_positive':3,
    'exact_bad_artifacts':len(artifacts),'green_product_counts':[4,37,2],
    'zero_errors_skips':True,'image_inflation':'NOT_RUN'}))
