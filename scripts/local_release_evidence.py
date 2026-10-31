"""Keep original local fixture diagnostics private; leave CI temp cleanup intact."""
from contextlib import contextmanager
import os
from pathlib import Path
import re
import stat
import tempfile

from local_release_context import source_context


def _local_audit(prefix):
    local_keys = ("RCT_LOCAL_RELEASE_REVISION", "RCT_LOCAL_RELEASE_NONCE", "RCT_LOCAL_RELEASE_AUDIT")
    if not any(key in os.environ for key in local_keys):
        return None
    context = source_context()
    if context["event"] != "LOCAL_CONTROL":
        raise ValueError("Evidence retention requires an honest local context")
    if re.fullmatch(r"rct-[a-z0-9-]{1,96}-", prefix) is None:
        raise ValueError("Finite fixture evidence prefix required")
    audit = Path(os.environ["RCT_LOCAL_RELEASE_AUDIT"])
    # source_context validates the final directory's owner/mode and source SHA.
    # Also reject path aliases through parent symlinks or parent traversal.
    if ".." in audit.parts:
        raise ValueError("Canonical private audit path required")
    for parent in (audit, *audit.parents):
        if not stat.S_ISDIR(parent.lstat().st_mode):
            raise ValueError("Audit ancestry must contain real directories")
    return audit


@contextmanager
def evidence_directory(*, prefix, dir=None):
    """Retain original local work, preserving nonroot fixture file modes.

    Owner-only directory ancestry protects even container-owned synthetic files.
    No raw content is copied, serialized, or added to public safe publishers.
    """
    audit = _local_audit(prefix)
    if audit is None:
        with tempfile.TemporaryDirectory(prefix=prefix, dir=dir) as directory:
            yield directory
        return
    # mkdtemp is atomic, unique, and 0700. No TemporaryDirectory finalizer is
    # attached in local mode, so even termination cannot discard produced logs.
    directory = tempfile.mkdtemp(prefix=prefix, dir=audit)
    yield directory


@contextmanager
def evidence_file(*, prefix):
    """Retain a unique original local binary log (0600); CI remains anonymous."""
    audit = _local_audit(prefix)
    if audit is None:
        with tempfile.TemporaryFile() as stream:
            yield stream
        return
    # NamedTemporaryFile uses atomic O_EXCL creation with mode0600. Closing the
    # descriptor on PASS/failure/cancel never unlinks the local evidence path.
    with tempfile.NamedTemporaryFile(mode="w+b", prefix=prefix, dir=audit, delete=False) as stream:
        yield stream
