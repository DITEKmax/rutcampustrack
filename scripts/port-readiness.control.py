"""Bounded port readiness controls only. No Docker, browser or application."""
import errno
import importlib.util
from pathlib import Path
import socket
import tempfile
import time
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('lane', Path(__file__).with_name('local_e2e_async.py'))
lane = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lane)


class PortReadinessControls(unittest.TestCase):
    def fixture(self, tcp='', tcp6=''):
        temporary = tempfile.TemporaryDirectory(prefix='rct-port-proc-')
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        header = '  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt uid timeout inode\n'
        for name, rows in (('tcp', tcp), ('tcp6', tcp6)):
            (root / name).write_text(header + rows)
        return root

    def row(self, address='0100007F', state='06', port=8443):
        return f'0: {address}:{port:04X} {"0" * len(address)}:0000 {state} 0:0 0:0 0 0 0 0 0 0\n'

    def test_exact_ipv4_and_dual_stack_conflicts(self):
        for address in ('0100007F', '00000000', '0' * 32, '0000000000000000FFFF00000100007F'):
            with self.subTest(address=address):
                root = self.fixture(**({'tcp': self.row(address)} if len(address) == 8 else {'tcp6': self.row(address)}))
                self.assertEqual(lane.port_tcp_state(8443, proc=root), 'TIME_WAIT_ONLY')
                self.assertEqual(lane.port_tcp_state(8088, proc=root), 'UNKNOWN')
        root = self.fixture(tcp=self.row('0200007F'))
        self.assertEqual(lane.port_tcp_state(8443, proc=root), 'UNKNOWN')

    def test_listener_and_other_take_precedence(self):
        for state, expected in (('0A', 'LISTENER'), ('01', 'OTHER_TCP_STATE'), ('FF', 'OTHER_TCP_STATE')):
            root = self.fixture(tcp=self.row(), tcp6=self.row('0' * 32, state))
            self.assertEqual(lane.port_tcp_state(8443, proc=root), expected)

    def test_missing_malformed_truncated_and_oversized_are_unknown(self):
        for raw in (b'', b'local_address rem_address\ntruncated', b'local_address rem_address\nbad\n',
                    b'local_address rem_address\n' + b' ' * (4 * 1024**2) + b'\n'):
            root = self.fixture(tcp=self.row())
            (root / 'tcp6').write_bytes(raw)
            self.assertEqual(lane.port_tcp_state(8443, proc=root), 'UNKNOWN')
        root = self.fixture(tcp=self.row())
        (root / 'tcp6').unlink()
        self.assertEqual(lane.port_tcp_state(8443, proc=root), 'UNKNOWN')

    def failure(self, number=errno.EADDRINUSE):
        return OSError(number, 'PRIVATE_PORT_CONTROL_CANARY')

    def test_success_requires_a_later_strict_bind(self):
        original = self.failure()
        with patch.object(lane, 'strict_port_bind', side_effect=[original, None]) as bind, \
             patch.object(lane, 'port_tcp_state', return_value='TIME_WAIT_ONLY') as state, \
             patch.object(lane.time, 'monotonic', return_value=10), patch.object(lane.time, 'sleep') as sleep:
            lane.port_ready(8443, 11)
            self.assertEqual(bind.call_count, 2)
            state.assert_called_once_with(8443)
            sleep.assert_called_once_with(.2)

    def test_listener_other_unknown_and_non_address_error_never_wait(self):
        for state in ('LISTENER', 'OTHER_TCP_STATE', 'UNKNOWN'):
            original = self.failure()
            with self.subTest(state=state), patch.object(lane, 'strict_port_bind', side_effect=original), \
                 patch.object(lane, 'port_tcp_state', return_value=state), patch.object(lane.time, 'sleep') as sleep:
                with self.assertRaises(OSError) as caught:
                    lane.port_ready(8443, 100)
                self.assertIs(caught.exception, original)
                self.assertEqual(lane.failure_diagnostic(original)['portTcpState'], state)
                self.assertNotIn('CANARY', str(lane.failure_diagnostic(original)))
                sleep.assert_not_called()
        original = self.failure(errno.EACCES)
        with patch.object(lane, 'strict_port_bind', side_effect=original), patch.object(lane, 'port_tcp_state') as state:
            with self.assertRaises(OSError) as caught:
                lane.port_ready(8443, 100)
            self.assertIs(caught.exception, original)
            self.assertEqual(original.rct_port_readiness, 'BIND_ERROR')
            state.assert_not_called()

    def test_timewait_transition_and_budget_preserve_first_error(self):
        for observations, clocks, expected in ((['TIME_WAIT_ONLY', 'LISTENER'], [0], 'NOT_TIME_WAIT_ONLY'),
                                                 (['TIME_WAIT_ONLY', 'TIME_WAIT_ONLY'], [0, 1], 'BUDGET_EXPIRED')):
            original, later = self.failure(), self.failure()
            with patch.object(lane, 'strict_port_bind', side_effect=[original, later]), \
                 patch.object(lane, 'port_tcp_state', side_effect=observations), \
                 patch.object(lane.time, 'monotonic', side_effect=clocks), patch.object(lane.time, 'sleep') as sleep:
                with self.assertRaises(OSError) as caught:
                    lane.port_ready(8443, 1)
                self.assertIs(caught.exception, original)
                self.assertEqual(original.rct_port_readiness, expected)
                sleep.assert_called_once_with(.2)

    def test_real_own_ephemeral_listener_fails_without_waiting(self):
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            listener.listen()
            port = listener.getsockname()[1]
            self.assertEqual(lane.port_tcp_state(port), 'LISTENER')
            with patch.object(lane.time, 'sleep') as sleep:
                with self.assertRaises(OSError) as caught:
                    lane.port_ready(port, time.monotonic() + 65)
                self.assertEqual(caught.exception.errno, errno.EADDRINUSE)
                self.assertEqual(caught.exception.rct_port_readiness, 'NOT_TIME_WAIT_ONLY')
                sleep.assert_not_called()

    def test_real_own_ephemeral_timewait_blocks_strict_bind(self):
        with socket.socket() as listener, socket.socket() as client:
            listener.bind(('127.0.0.1', 0))
            listener.listen()
            port = listener.getsockname()[1]
            client.connect(('127.0.0.1', port))
            accepted, _ = listener.accept()
            accepted.shutdown(socket.SHUT_WR)
            self.assertEqual(client.recv(1), b'')
            client.close()
            accepted.close()
        self.assertEqual(lane.port_tcp_state(port), 'TIME_WAIT_ONLY')
        with self.assertRaises(OSError) as caught:
            lane.strict_port_bind(port)
        self.assertEqual(caught.exception.errno, errno.EADDRINUSE)
        # Do not wait a minute: deadline is covered deterministically above.
        with patch.object(lane.time, 'sleep') as sleep:
            with self.assertRaises(OSError) as caught:
                lane.port_ready(port, time.monotonic())
            self.assertEqual(caught.exception.rct_port_readiness, 'BUDGET_EXPIRED')
            sleep.assert_not_called()


if __name__ == '__main__':
    unittest.main()
