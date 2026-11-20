"""Local, queued replay workbench. HTTP and orchestration use only stdlib."""
import html
import json
import os
import queue
import re
import subprocess
import sys
import threading
import uuid
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from lab.replay import CHAIN, RULE_DIR, validate

STATIC = ROOT / 'lab/static/index.html'
JOBS = {}
LOCK = threading.Lock()
QUEUE = queue.Queue()
MAX_BODY = 2 * 1024 * 1024


def command(args, cwd=ROOT):
    try:
        result = subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=45)
        return (result.stdout + result.stderr).strip() if result.returncode == 0 else f'unavailable (exit {result.returncode})'
    except (OSError, subprocess.TimeoutExpired) as error:
        return f'unavailable ({error})'


def startup_metadata():
    ns = {'p': 'http://maven.apache.org/POM/4.0.0'}
    module = ROOT / 'java/interest/interest-batch'
    pom = ET.parse(module / 'pom.xml').getroot()
    boot = pom.findtext('p:parent/p:version', namespaces=ns)
    batch = pom.findtext('p:properties/p:spring-batch.version', namespaces=ns)
    if not batch:
        inherited = Path.home() / f'.m2/repository/org/springframework/boot/spring-boot-dependencies/{boot}/spring-boot-dependencies-{boot}.pom'
        if inherited.exists():
            batch = ET.parse(inherited).getroot().findtext('p:properties/p:spring-batch.version', namespaces=ns)
    if not batch:
        batch = command([os.environ.get('MVN', 'mvn'), '-q', 'help:evaluate', '-Dexpression=spring-batch.version', '-DforceStdout'], module)
    return {
        'sha': command(['git', 'rev-parse', 'HEAD']),
        'dirty': bool(command(['git', 'status', '--porcelain'])),
        'clock': CHAIN['clock'], 'parm': '2022071800',
        'toolchain': {
            'GnuCOBOL': command(['docker', 'compose', 'exec', '-T', 'estate', 'cobc', '--version']).splitlines()[0],
            'Java (estate)': command(['docker', 'compose', 'exec', '-T', 'estate', '/usr/lib/jvm/java-21-openjdk-amd64/bin/java', '-version']).splitlines()[0],
            'Python (host)': sys.version.split()[0],
            'PostgreSQL server': command(['docker', 'compose', 'exec', '-T', 'db', 'psql', '-U', 'carddemo', '-d', 'carddemo', '-Atc', 'SHOW server_version']),
            'Maven (host)': command([os.environ.get('MVN', 'mvn'), '--version']).splitlines()[0],
            'Spring Boot (pom)': boot, 'Spring Batch (inherited pom)': batch,
        },
        'golden': [{'id': p.name, 'title': p.name} for p in sorted((ROOT / 'fixtures/interest').iterdir()) if (p / 'input').is_dir()],
        'probes': [dict(json.loads((p / 'probe.json').read_text()), readme=(p / 'README.md').read_text())
                   for p in sorted((ROOT / 'lab/probes').iterdir()) if (p / 'probe.json').exists()],
        'datasets': {name + '.txt': length for name, length in CHAIN['inputs'].items()},
        'rule_ids': list(json.loads((ROOT / 'lab/coverage.json').read_text())),
    }


def safe_file(relative):
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT) or not path.is_file():
        raise ValueError('file not found or outside repository')
    return path


def register_page():
    rows = []
    for line in (RULE_DIR / 'decisions.md').read_text().splitlines():
        if line.startswith('|'):
            cells = [v.strip() for v in line.strip('|').split('|')]
            if all(re.fullmatch(r'[-:]+', c) for c in cells):
                continue
            header = cells[0] == 'ID'
            tag = 'th' if header else 'td'
            ident = f' id="{html.escape(cells[0])}"' if re.fullmatch(r'INT-[QH]\d+', cells[0]) else ''
            formatted = [html.escape(c).replace('&lt;br&gt;', '<br>') for c in cells]
            rows.append(f'<tr{ident}>' + ''.join(f'<{tag}>{c}</{tag}>' for c in formatted) + '</tr>')
    return ('<!doctype html><html lang="en"><meta charset="utf-8"><title>Interest decision register</title>'
            '<link rel="icon" href="data:,">'
            '<style>body{font:15px system-ui;margin:32px;color:#183036;background:#f5f6f2}table{border-collapse:collapse;background:white}th,td{border:1px solid #ccd6d0;padding:14px;text-align:left;vertical-align:top}th{background:#193f39;color:white}tr:target{background:#fff1b5;outline:3px solid #a76c00}a{color:#006d5d}</style>'
            '<h1>Interest decision register</h1><p>Not SME sign-off. Status is authoritative; replay is evidence, not policy.</p>'
            '<a href="/">Replay lab</a><table>' + ''.join(rows) + '</table></html>')


def worker():
    while True:
        run_id, arguments = QUEUE.get()
        with LOCK:
            JOBS[run_id]['status'] = 'running'
        out = ROOT / 'work/lab/runs' / run_id
        try:
            out.mkdir(parents=True, exist_ok=True)
            args = [sys.executable, str(ROOT / 'lab/replay.py'), *arguments, '--run-id', run_id,
                    '--selected-gate', '--json', str(out / 'report.json'), '--report', str(out / 'report.md')]
            process = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=900)
            (out / 'coordinator.log').write_text(process.stdout + process.stderr)
            report_file = out / 'report.json'
            if not report_file.exists():
                raise RuntimeError((process.stdout + process.stderr)[-5000:] or f'replay exited {process.returncode}')
            result = json.loads(report_file.read_text())
            with LOCK:
                JOBS[run_id].update(status='complete', result=result)
        except Exception as error:
            with LOCK:
                JOBS[run_id].update(status='error', error=str(error))
        finally:
            QUEUE.task_done()


class Handler(BaseHTTPRequestHandler):
    def send(self, data, status=200, content_type='application/json; charset=utf-8'):
        if isinstance(data, (dict, list)):
            data = json.dumps(data)
        if isinstance(data, str):
            data = data.encode()
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        url = urlsplit(self.path)
        path = unquote(url.path)
        try:
            if path == '/':
                return self.send(STATIC.read_bytes(), content_type='text/html; charset=utf-8')
            if path == '/api/meta':
                return self.send(self.server.metadata)
            if path == '/api/inputs':
                case = parse_qs(url.query).get('case', [''])[0]
                if case not in {c['id'] for c in self.server.metadata['golden']}:
                    raise ValueError('unknown golden case')
                directory = ROOT / 'fixtures/interest' / case / 'input'
                return self.send({name: (directory / name).read_text(encoding='latin-1') for name in self.server.metadata['datasets']})
            if path.startswith('/api/runs/'):
                run_id = path.removeprefix('/api/runs/')
                with LOCK:
                    job = dict(JOBS.get(run_id, {}))
                if not job:
                    return self.send({'error': 'run not found'}, 404)
                return self.send(job)
            if path == '/register':
                return self.send(register_page(), content_type='text/html; charset=utf-8')
            if path in ('/board', '/board/'):
                return self.send((RULE_DIR / 'rules-INTCALC.html').read_bytes(), content_type='text/html; charset=utf-8')
            if path.startswith('/evidence/'):
                relative = path.removeprefix('/evidence/')
                file = safe_file(relative)
                # Evidence is a narrow read-only mount, not a general repository browser.
                if not file.is_relative_to(ROOT / 'work/lab/runs'):
                    raise ValueError('not a replay artifact')
                return self.send(file.read_bytes(), content_type='text/plain; charset=utf-8')
            return self.send({'error': 'not found'}, 404)
        except (ValueError, OSError) as error:
            return self.send({'error': str(error)}, 404)

    def do_POST(self):
        if urlsplit(self.path).path != '/api/runs':
            return self.send({'error': 'not found'}, 404)
        custom = False
        try:
            # This local tool accepts only same-origin browser submissions.
            origin = self.headers.get('Origin')
            if origin and urlsplit(origin).netloc != self.headers.get('Host'):
                return self.send({'error': 'cross-origin submission refused'}, 403)
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= MAX_BODY:
                raise ValueError('request body must be between 1 byte and 2 MiB')
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict) or len(set(body) & {'case', 'set', 'inputs'}) != 1:
                raise ValueError('choose exactly one case, set, or custom inputs')
            run_id = uuid.uuid4().hex
            if 'case' in body:
                if body['case'] not in {c['id'] for c in self.server.metadata['golden'] + self.server.metadata['probes']}:
                    raise ValueError('unknown case')
                arguments = ['run', '--case', body['case']]
            elif 'set' in body:
                if body['set'] not in ('golden', 'edge', 'all'):
                    raise ValueError('unknown case set')
                arguments = ['gate', '--set', body['set']]
            else:
                custom = True
                inputs = body['inputs']
                if not isinstance(inputs, dict) or set(inputs) != set(self.server.metadata['datasets']):
                    raise ValueError('custom input must contain exactly the four named datasets')
                if not all(isinstance(v, str) for v in inputs.values()):
                    raise ValueError('each dataset must be text')
                directory = ROOT / 'work/lab/custom' / run_id / 'input'
                directory.mkdir(parents=True)
                for name, text in inputs.items():
                    try:
                        (directory / name).write_text(text, encoding='latin-1')
                    except UnicodeEncodeError as error:
                        raise ValueError(f'{name}: use Latin-1 characters for the estate codec') from error
                validate(directory)
                arguments = ['run', '--input-dir', str(directory)]
            with LOCK:
                JOBS[run_id] = {'id': run_id, 'status': 'queued'}
            QUEUE.put((run_id, arguments))
            return self.send({'id': run_id, 'status': 'queued'}, 202)
        except (ValueError, TypeError, OSError) as error:
            return self.send({'ok': False, 'error': str(error)}, 200 if custom else 400)


def main():
    port = int(os.environ.get('LAB_PORT', '8090'))
    server = ThreadingHTTPServer(('0.0.0.0', port), Handler)
    server.metadata = startup_metadata()
    threading.Thread(target=worker, daemon=True).start()
    print(f'Interest replay lab: http://localhost:{port}', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
