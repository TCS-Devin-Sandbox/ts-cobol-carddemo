"""Isolated workbench and CLI comparison evidence for the twelve UI rows."""
import json
import os
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path

import pytest
from playwright.sync_api import expect

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'lab/evidence'


@pytest.fixture(scope='session')
def gate_report():
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(['make', 'lab-gate'], cwd=ROOT, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=600)
    (EVIDENCE / 'gate-output.txt').write_text(result.stdout + f'\nExit code: {result.returncode}\n')
    assert result.returncode == 0, result.stdout
    for source, destination in [('report.md', 'classified-report.md'), ('report.json', 'report.json')]:
        shutil.copyfile(ROOT / 'work/lab' / source, EVIDENCE / destination)
    return json.loads((ROOT / 'work/lab/report.json').read_text())


@pytest.fixture(scope='session')
def lab_url(gate_report):
    if os.environ.get('LAB_URL'):
        yield os.environ['LAB_URL'].rstrip('/')
        return
    with socket.socket() as reservation:
        reservation.bind(('127.0.0.1', 0))
        port = reservation.getsockname()[1]
    log_path = ROOT / 'work/lab/e2e-server.log'
    with log_path.open('w') as log:
        process = subprocess.Popen([sys.executable, 'lab/server.py'], cwd=ROOT,
                                   env={**os.environ, 'LAB_PORT': str(port)}, stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 90
            while True:
                assert process.poll() is None, log_path.read_text()
                try:
                    with socket.create_connection(('127.0.0.1', port), timeout=1):
                        break
                except OSError:
                    if time.monotonic() >= deadline:
                        pytest.fail('Lab startup timed out: ' + log_path.read_text())
                    time.sleep(0.1)
            yield f'http://127.0.0.1:{port}'
        finally:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


@pytest.fixture
def browser_context_args(browser_context_args):
    return {**browser_context_args, 'viewport': {'width': 1600, 'height': 1250}}


@pytest.fixture
def workbench(page, lab_url):
    errors = []
    page.on('console', lambda message: errors.append(message.text) if message.type == 'error' else None)
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(lab_url)
    expect(page.locator('#golden option')).to_have_count(7, timeout=60000)
    yield page
    assert not errors, errors
