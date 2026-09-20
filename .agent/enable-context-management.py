"""Enable only the requested user-config key, with an exclusive backup."""
import argparse
import copy
import datetime
import hashlib
import json
import pathlib
import re
import shutil
import tomllib

parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()
config = pathlib.Path.home() / '.codex' / 'config.toml'
if config.is_symlink():
    raise SystemExit('Symlink requires explicit target inspection; no changes made.')
original = config.read_bytes()
text = original.decode('utf-8-sig')
before = tomllib.loads(text)
features = before.get('features', {})
context = features.get('context_management', {})
if not isinstance(context, dict):
    raise SystemExit('Existing context_management is not a table; targeted merge required.')
if context.get('experimental_mode') is True:
    print(json.dumps({'status': 'already enabled', 'path': str(config)}))
    raise SystemExit(0)
newline = '\r\n' if '\r\n' in text else '\n'
header = re.compile(r'^\[features\.context_management\][ \t]*(?:#.*)?$', re.M)
matches = list(header.finditer(text.replace('\r\n', '\n')))
# This installation currently has no context-management table. Handle a normal
# existing table too, while refusing unusual dotted/inline representations.
normalized = text.replace('\r\n', '\n')
if len(matches) > 1:
    raise SystemExit('Duplicate table; no changes made.')
if matches:
    start = matches[0].end()
    next_header = re.search(r'^\s*\[', normalized[start:], re.M)
    end = start + next_header.start() if next_header else len(normalized)
    section = normalized[start:end]
    key = re.compile(r'^(\s*experimental_mode\s*=\s*)(true|false)([ \t]*(?:#.*)?)$', re.M)
    if key.search(section):
        section = key.sub(r'\g<1>true\3', section, count=1)
    elif 'experimental_mode' not in context:
        section = '\nexperimental_mode = true' + section
    else:
        raise SystemExit('Nonstandard key syntax; no changes made.')
    normalized = normalized[:start] + section + normalized[end:]
else:
    if context or 'context_management' in features:
        raise SystemExit('Nonstandard table syntax; no changes made.')
    normalized += ('' if normalized.endswith('\n') else '\n') + '\n[features.context_management]\nexperimental_mode = true\n'
updated = normalized.replace('\n', newline).encode('utf-8')
if original.startswith(b'\xef\xbb\xbf'):
    updated = b'\xef\xbb\xbf' + updated
expected = copy.deepcopy(before)
expected.setdefault('features', {}).setdefault('context_management', {})['experimental_mode'] = True
assert tomllib.loads(updated.decode('utf-8-sig')) == expected
result = {'path': str(config), 'before_bytes': len(original), 'after_bytes': len(updated), 'only_semantic_change': 'features.context_management.experimental_mode = true'}
if args.apply:
    timestamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup = config.with_name(config.name + '.bak-context-management-' + timestamp)
    # Exclusive creation never overwrites an earlier backup.
    with backup.open('xb') as out:
        out.write(original)
    shutil.copystat(config, backup)
    assert backup.read_bytes() == original
    # Preserve file identity/ACL and refuse concurrent changes before writing.
    with config.open('r+b') as out:
        if out.read() != original:
            raise SystemExit('Configuration changed concurrently; preserved backup, no write.')
        out.seek(0)
        out.write(updated)
        out.truncate()
        out.flush()
    assert config.read_bytes() == updated
    assert tomllib.loads(config.read_text(encoding='utf-8-sig')) == expected
    result.update(status='configuration written and reread', backup=str(backup), backup_sha256=hashlib.sha256(original).hexdigest())
else:
    result['status'] = 'plan only'
print(json.dumps(result, ensure_ascii=False, indent=2))
