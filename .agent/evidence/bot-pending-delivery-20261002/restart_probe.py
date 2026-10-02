"""One isolated real Rabbit/Redis bot-process crash probe; provider is a file recorder.
Run only under the root heavy lease with owned localhost test containers.
"""
import argparse
import asyncio
import json
import os
from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--root', required=True)
parser.add_argument('--output', required=True)
parser.add_argument('--rabbit-port', type=int, required=True)
parser.add_argument('--redis-port', type=int, required=True)
parser.add_argument('--child', choices=['blocked', 'replay'])
args = parser.parse_args()
root = Path(args.root).resolve()
output = Path(args.output).resolve()
if output.parent != root / '.agent/evidence/bot-pending-delivery-20261002':
    raise ValueError('Evidence output must stay in this task scope')
if not (1024 < args.rabbit_port < 65536 and 1024 < args.redis_port < 65536):
    raise ValueError('Explicit isolated test ports required')
output.mkdir(exist_ok=True)
sys.path.insert(0, str(root / 'services/notification-bot'))
import aio_pika
import redis.asyncio as redis
from aio_pika.exceptions import QueueEmpty
from bot.consumers.event_consumer import start_consumer, QUEUE_NAME, EXCHANGE_NAME, DLQ_QUEUE_NAME
from bot.services.idempotency_guard import BotIdempotencyGuard
from bot.services.send_queue import TelegramSendQueue, SendTask

url = f'amqp://guest:guest@127.0.0.1:{args.rabbit_port}/'

async def child():
    async def enabled(*unused, **kwargs):
        return True
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=enabled))
    if args.child == 'blocked':
        async def blocked_rate():
            (output / 'blocked-ready').write_text('admitted before provider attempt', encoding='utf-8')
            await asyncio.Event().wait()
        queue._consume_token = blocked_rate
    queue.start()
    guard = BotIdempotencyGuard(host='127.0.0.1', port=args.redis_port,
                               ttl_seconds=3, completion_ttl_seconds=60)
    async def provider_recorder():
        # This is a controlled local attempt, never Telegram/network provider I/O.
        with (output / 'attempts.jsonl').open('a', encoding='utf-8') as stream:
            stream.write(json.dumps({'attempt': 'local-recorder'}) + '\n')
        return SimpleNamespace(message_id=1)
    async def current_audience():
        return True
    async def dispatch(event):
        async with queue.staged() as batch:
            await queue.put(SendTask(provider_recorder, user_id=11, chat_id=111,
                                     category='tickets', audience_check=current_audience))
        return batch
    try:
        await start_consumer(url, SimpleNamespace(dispatch=dispatch), guard)
    finally:
        await queue.shutdown()
        await guard.close()

async def wait_until(check, label, seconds=25):
    deadline = asyncio.get_running_loop().time() + seconds
    while asyncio.get_running_loop().time() < deadline:
        if await check():
            return
        await asyncio.sleep(.1)
    raise AssertionError('Timed out: ' + label)

async def probe():
    clients = []
    logs = []
    connection = None
    cache = redis.Redis(host='127.0.0.1', port=args.redis_port, decode_responses=True)
    def spawn(mode):
        stream = (output / (mode + '.log')).open('w', encoding='utf-8')
        logs.append(stream)
        env = dict(os.environ)
        env['PYTHONPATH'] = str(root / 'services/notification-bot')
        process = subprocess.Popen([sys.executable, __file__, '--root', str(root),
            '--output', str(output), '--rabbit-port', str(args.rabbit_port),
            '--redis-port', str(args.redis_port), '--child', mode], cwd=root,
            env=env, stdout=stream, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW)
        clients.append(process)
        return process
    try:
        await cache.ping()
        connection = await aio_pika.connect(url)
        channel = await connection.channel()
        # Exact production declarations, isolated empty broker. No purge/flush/reset.
        exchange = await channel.declare_exchange(EXCHANGE_NAME, aio_pika.ExchangeType.FANOUT, durable=True)
        dlx = await channel.declare_exchange('rut-uit.events.dlq', aio_pika.ExchangeType.DIRECT, durable=True)
        dlq = await channel.declare_queue(DLQ_QUEUE_NAME, durable=True,
            arguments={'x-message-ttl': 604800000, 'x-max-length': 10000, 'x-overflow': 'drop-head'})
        await dlq.bind(dlx, routing_key=DLQ_QUEUE_NAME)
        queue = await channel.declare_queue(QUEUE_NAME, durable=True,
            arguments={'x-dead-letter-exchange': 'rut-uit.events.dlq', 'x-dead-letter-routing-key': DLQ_QUEUE_NAME})
        await queue.bind(exchange)
        event_id = str(uuid.uuid4())
        key = 'consumed:notification-bot:' + event_id
        first = spawn('blocked')
        await exchange.publish(aio_pika.Message(json.dumps({'event_id': event_id,
            'event_type': 'synthetic.pending'}).encode(), delivery_mode=aio_pika.DeliveryMode.PERSISTENT), routing_key='')
        async def ready():
            assert first.poll() is None, 'First process exited before admission'
            return (output / 'blocked-ready').exists()
        await wait_until(ready, 'first admission')
        assert (await cache.get(key) or '').startswith('processing:'), 'Early completed claim'
        assert not (output / 'attempts.jsonl').exists(), 'Provider attempted before crash'
        first.kill()
        first.wait(timeout=10)
        redelivery = None
        async def returned():
            nonlocal redelivery
            try:
                redelivery = await queue.get(timeout=2, fail=True)
                return True
            except QueueEmpty:
                return False
        await wait_until(returned, 'original returned after hard process crash')
        assert redelivery.redelivered, 'Rabbit did not mark original as redelivery'
        assert json.loads(redelivery.body)['event_id'] == event_id
        await redelivery.nack(requeue=True)
        second = spawn('replay')
        async def complete():
            assert second.poll() is None, 'Replay process exited'
            return await cache.get(key) == 'completed'
        await wait_until(complete, 'replayed batch completed')
        await asyncio.sleep(.2)
        second.kill()
        second.wait(timeout=10)
        async def acked():
            result = await channel.declare_queue(QUEUE_NAME, passive=True)
            return result.declaration_result.message_count == 0 and result.declaration_result.consumer_count == 0
        await wait_until(acked, 'original ACK')
        attempts = (output / 'attempts.jsonl').read_text(encoding='utf-8').splitlines()
        assert len(attempts) == 1, 'Local recorder did not execute exactly once in this pre-attempt crash scenario'
        result = await channel.declare_queue(DLQ_QUEUE_NAME, passive=True)
        assert result.declaration_result.message_count == 0, 'Unexpected DLQ event'
        evidence = {'result': 'PASS', 'event_id': event_id, 'first_process_exit': first.returncode,
            'persistent_original_redelivered': True, 'pre_crash_provider_attempts': 0,
            'replay_local_recorder_attempts': len(attempts), 'claim': 'completed',
            'main_ready': 0, 'dlq_ready': 0, 'provider': 'controlled local file recorder',
            'authority': 'controlled known binding/preferences/audience',
            'limits': 'Real Rabbit/Redis and bot process crash only; no upstream RPC/provider/exactly-once/DR claim'}
        (output / 'result.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(evidence))
    finally:
        for process in clients:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=10)
        for stream in logs:
            stream.close()
        if connection is not None:
            await connection.close()
        await cache.aclose()

if __name__ == '__main__':
    asyncio.run(child() if args.child else probe())
