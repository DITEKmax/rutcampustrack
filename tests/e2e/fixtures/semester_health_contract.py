"""Closed sidecar schema. No raw health output or identity fields are accepted."""
import json
import os
from pathlib import Path
import stat

REASONS = frozenset('''ROWS_TYPE ROWS_EMPTY ROWS_LIMIT ROW_NULL ROW_TYPE ROW_ARRAY
ROW_PROTOTYPE ROW_KEYS EXIT_TYPE EXIT_NOT_ZERO START_TYPE END_TYPE OUTPUT_TYPE OUTPUT_SIZE
SINCE_NONFINITE OBSERVED_NONFINITE START_NON_Z START_FORMAT END_NON_Z END_FORMAT START_DATE END_DATE
START_AFTER_END END_AFTER_OBSERVED START_NONCANONICAL END_NONCANONICAL SUBMILLI_ORDER BODY_JSON
BODY_STATUS GRPC_STATUS DETAILS_NULL DETAILS_TYPE DETAILS_ARRAY SCHEDULE_MISSING STATE_NOT_ALLOWED
STALE_SNAPSHOT OBSERVED_READY OBSERVED_IDLE OBSERVED_CONNECTING OBSERVED_TRANSIENT_FAILURE
OBSERVED_SHUTDOWN UNEXPECTED RESULT_MISMATCH'''.split())
STATES = frozenset(('READY', 'IDLE', 'CONNECTING', 'TRANSIENT_FAILURE', 'SHUTDOWN'))


def require(value):
    if not value:
        raise ValueError('Invalid closed health sidecar')


def observation(value):
    require(type(value) is dict and set(value) == {'reason', 'channelState', 'fresh'})
    reason, state, fresh = value['reason'], value['channelState'], value['fresh']
    require(type(reason) is str and reason in REASONS and type(state) is str)
    if reason.startswith('OBSERVED_'):
        require(state in STATES and reason == 'OBSERVED_' + state and fresh is True)
    elif reason == 'STALE_SNAPSHOT':
        require(state in STATES and fresh is False)
    else:
        require(state == 'UNKNOWN' and fresh is None)


def validate(value):
    require(type(value) is dict and set(value) == {'schema', 'MAIN', 'CLEANUP'}
            and value['schema'] == 'semester-health-observation.v1')
    for phase in ('MAIN', 'CLEANUP'):
        require(type(value[phase]) is dict and set(value[phase]) == {'ATTENDANCE', 'ACADEMIC'})
        for side in ('ATTENDANCE', 'ACADEMIC'):
            row = value[phase][side]
            require(type(row) is dict and set(row) == {'observations', 'truncated', 'firstUnknown', 'last', 'counts'})
            require(type(row['observations']) is int and 0 <= row['observations'] <= 4096
                    and type(row['truncated']) is bool and type(row['counts']) is dict)
            require(all(type(k) is str and k in REASONS and type(n) is int and 1 <= n <= 4096
                        for k, n in row['counts'].items()))
            require(sum(row['counts'].values()) == row['observations'])
            if row['truncated']:
                require(row['observations'] == 4096)
            if row['observations'] == 0:
                require(row['last'] is None and row['firstUnknown'] is None)
            else:
                observation(row['last'])
            if row['firstUnknown'] is not None:
                observation(row['firstUnknown'])
                require(row['firstUnknown']['channelState'] == 'UNKNOWN')
    return value


def loads(raw):
    require(type(raw) is bytes and 0 < len(raw) <= 8192)
    def pairs(items):
        value = {}
        for key, field in items:
            require(key not in value)
            value[key] = field
        return value
    return validate(json.loads(raw, object_pairs_hook=pairs,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite health sidecar'))))


def load(path):
    path = Path(path)
    require(path.resolve() == path and not path.is_symlink())
    with os.fdopen(os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0)), 'rb') as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= 8192)
        if os.name == 'posix':
            require(info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o600)
        raw = stream.read(8193)
        require(len(raw) == info.st_size)
    return loads(raw)
