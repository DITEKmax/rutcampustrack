"""T006 STAGE-only allocation sampling; fresh cheap resources/clock at every effect."""
import os
from pathlib import Path
import stat
import time

INTERVAL = 1.0
EFFECTS = 64
ONE_EFFECT_MAX = 1048576
HEADROOM = EFFECTS * ONE_EFFECT_MAX
METADATA_ALLOWANCE = 65536


def full_inventory(prefix):
    records = {}
    seen = set()
    allocated = 0
    def fail(error):
        raise error
    for parent, dirs, files in os.walk(prefix, followlinks=False, onerror=fail):
        for p in [Path(parent)] + [Path(parent) / name for name in dirs + files]:
            s = p.lstat()
            if not (stat.S_ISDIR(s.st_mode) or stat.S_ISREG(s.st_mode)) or s.st_uid != 1000 or s.st_gid != 1000:
                raise StageStop('FOREIGN_PREFIX_NODE')
            relative = str(p.relative_to(prefix))
            records[relative] = {'kind': 'directory' if stat.S_ISDIR(s.st_mode) else 'regular',
                                 'device': s.st_dev, 'inode': s.st_ino, 'uid': s.st_uid, 'gid': s.st_gid,
                                 'mode': stat.S_IMODE(s.st_mode), 'nlink': s.st_nlink,
                                 'bytes': s.st_size, 'blocks': s.st_blocks * 512,
                                 'mtime_ns': s.st_mtime_ns, 'ctime_ns': s.st_ctime_ns}
            key = (s.st_dev, s.st_ino)
            if key not in seen:
                allocated += s.st_blocks * 512
                seen.add(key)
    return allocated, records


class StageStop(RuntimeError):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


class Monitor:
    def __init__(self, boundary, grant, stop, baseline, inventory_reader=None):
        self.b = boundary
        self.g = grant
        self.stop = stop
        self.baseline = baseline
        self.actual = None
        self.last = None
        self.effects = 0
        self.pending_upper = 0
        self.inventory_count = 0
        self.reader = inventory_reader
        self.base_records = None
        self.allowed_missing = None
        self.allowed_directories = None

    def inventory(self):
        started = time.monotonic()
        if self.reader is None:
            allocated = self.b.allocated(self.b.PREFIX)
        else:
            allocated, records = self.reader(self.b.PREFIX)
            if self.base_records is None:
                self.base_records = records
            elif self.allowed_missing is not None:
                self.reconcile(records)
        finished = time.monotonic()
        self.actual = allocated
        self.last = finished
        self.effects = 0
        self.pending_upper = 0
        self.inventory_count += 1
        return finished - started

    def configure(self, missing, directories):
        self.allowed_missing = {'app/' + row['path']: row['bytes'] for row in missing}
        self.allowed_directories = {'app/' + path for path in directories if path != '.'} | {'app'}

    def reconcile(self, records):
        stable = ['kind', 'device', 'inode', 'uid', 'gid', 'mode']
        for path, before in self.base_records.items():
            current = records.get(path)
            if current is None:
                raise StageStop('PROTECTED_PREFIX_NODE_MISSING')
            if before['kind'] == 'directory' and (path == '.' or path == 'app' or path.startswith('app/')):
                if any(current[k] != before[k] for k in stable):
                    raise StageStop('PROTECTED_DIRECTORY_CHANGED')
            elif current != before:
                raise StageStop('PROTECTED_PREFIX_NODE_CHANGED')
        for path, row in records.items():
            if path in self.base_records:
                continue
            if path in self.allowed_missing:
                if (row['kind'] != 'regular' or row['mode'] != 0o644 or row['nlink'] != 1
                        or row['bytes'] > self.allowed_missing[path]):
                    raise StageStop('NEW_MEMBER_BOUND')
            elif path in self.allowed_directories:
                if row['kind'] != 'directory' or row['mode'] != 0o755:
                    raise StageStop('NEW_DIRECTORY_BOUND')
            elif path in ['host-service-specs.safe.json', 'stage-result.safe.json', 'stage-committed.safe.json']:
                if row['kind'] != 'regular' or row['mode'] != 0o600 or row['nlink'] != 1 or row['bytes'] > ONE_EFFECT_MAX:
                    raise StageStop('NEW_RECEIPT_BOUND')
            else:
                raise StageStop('FOREIGN_PREFIX_ADDITION')

    def gate(self, *, force=False, effect_bytes=None, entry=False):
        cost = 0 if effect_bytes is None else effect_bytes
        if type(cost) is not int or not 0 <= cost <= ONE_EFFECT_MAX:
            raise StageStop('OWN_EFFECT_BOUND')
        elapsed = 0
        if (force or self.last is None or self.effects >= EFFECTS
                or time.monotonic() - self.last >= INTERVAL):
            elapsed = self.inventory()
        # Recollect cheap values AFTER any expensive scan, never return its old resource snapshot.
        cheap = self.b.cheap_resources()
        projected = self.actual + self.pending_upper + cost
        snapshot = dict(cheap, new_tmp_allocated=projected)
        try:
            self.b.require_resources(self.g, self.baseline, snapshot)
        except RuntimeError:
            raise StageStop('CHEAP_RESOURCE_OR_ALLOC_CAP') from None
        if cheap['tmp_free'] - cost < self.g['tmp_floor_plus_mongo_growth']:
            raise StageStop('OWN_EFFECT_TMP_HEADROOM')
        if entry and self.g['all_new_tmp_cap'] - self.actual < HEADROOM:
            raise StageStop('OWN_SAMPLE_HEADROOM')
        # A slow scan or slow cheap check cannot admit an effect with stale allocation metadata.
        if elapsed > INTERVAL or time.monotonic() - self.last > INTERVAL:
            raise StageStop('ALLOCATION_SAMPLE_STALE')
        try:
            self.b.fresh(self.stop)  # UTC LAST after allocation, resources and monotonic checks.
        except RuntimeError:
            raise StageStop('FRESH_WINDOW_STOP') from None
        if effect_bytes is not None:
            self.pending_upper += cost
            self.effects += 1
        return snapshot

    def facts(self):
        return {'actual_allocated_last_inventory': self.actual,
                'pending_own_effect_upper_bytes': self.pending_upper,
                'effects_since_inventory': self.effects,
                'inventory_count': self.inventory_count,
                'maximum_interval_seconds': INTERVAL, 'maximum_effects': EFFECTS,
                'maximum_single_effect_upper': ONE_EFFECT_MAX,
                'entry_sample_headroom': HEADROOM,
                'protected_metadata_reconciled': self.base_records is not None and self.allowed_missing is not None,
                'qualification': 'Sampled actual inode allocation plus conservative own-effect bookkeeping; '
                                 'same-UID writers and filesystem overhead are not hard OS isolation/caps.'}


def write_upper(size):
    if type(size) is not int or size < 0:
        raise StageStop('OWN_WRITE_SIZE')
    return ((size + 4095) // 4096) * 4096 + METADATA_ALLOWANCE
