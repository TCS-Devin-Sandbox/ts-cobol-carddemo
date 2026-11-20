import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from unittest.mock import patch

from lab.server import CHAIN, ROOT, Handler, register_page, safe_file


class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.server.metadata = {'datasets': {name + '.txt': length for name, length in CHAIN['inputs'].items()}}
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f'http://127.0.0.1:{cls.server.server_port}'

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def test_page_is_self_contained(self):
        with urlopen(self.url) as response:
            text = response.read().decode()
        self.assertIn('data:image/svg+xml', text)
        self.assertNotIn('<script src=', text)
        self.assertIn('Save page', text)

    def test_empty_request_returns_readable_error(self):
        request = Request(self.url + '/api/runs', data=b'{}', headers={'Content-Type': 'application/json'})
        with self.assertRaises(HTTPError) as error:
            urlopen(request)
        self.assertEqual(error.exception.code, 400)
        self.assertIn('choose exactly one', json.loads(error.exception.read())['error'])

    def test_untrusted_post_is_not_queued(self):
        for headers in [
            {'Host': 'attacker.example'},
            {'Host': 'attacker.example', 'Origin': 'http://attacker.example'},
            {'Referer': 'http://attacker.example/form'},
            {'Origin': self.url, 'Referer': 'http://attacker.example/form'},
            {'Origin': 'null'},
            {'Host': 'localhost:1'},
        ]:
            with self.subTest(headers=headers), patch('lab.server.QUEUE.put') as enqueue:
                request = Request(self.url + '/api/runs', data=b'{"set":"golden"}', headers=headers)
                with self.assertRaises(HTTPError) as error:
                    urlopen(request)
                self.assertEqual(error.exception.code, 403)
                enqueue.assert_not_called()

    def test_same_origin_post_is_queued(self):
        for headers in [{}, {'Origin': self.url}, {'Referer': self.url + '/'}]:
            with self.subTest(headers=headers), patch('lab.server.QUEUE.put') as enqueue:
                request = Request(self.url + '/api/runs', data=b'{"set":"golden"}', headers=headers)
                with urlopen(request) as response:
                    self.assertEqual(response.status, 202)
                enqueue.assert_called_once()

    def test_invalid_custom_input_names(self):
        request = Request(self.url + '/api/runs', data=b'{"inputs":{"../escape":"x"}}', headers={'Content-Type': 'application/json'})
        with urlopen(request) as response:
            self.assertEqual(response.status, 200)
            result = json.load(response)
        self.assertFalse(result['ok'])
        self.assertIn('four named datasets', result['error'])

    def custom_request(self, inputs):
        return Request(self.url + '/api/runs', data=json.dumps({'inputs': inputs}).encode(),
                       headers={'Content-Type': 'application/json'})

    def test_empty_custom_input_is_readable(self):
        with urlopen(self.custom_request({})) as response:
            self.assertEqual(response.status, 200)
            result = json.load(response)
        self.assertFalse(result['ok'])
        self.assertIn('four named datasets', result['error'])

    def test_invalid_custom_record_is_not_queued(self):
        inputs = {name: 'x' * (length + 1) for name, length in self.server.metadata['datasets'].items()}
        with patch('lab.server.QUEUE.put') as enqueue, urlopen(self.custom_request(inputs)) as response:
            self.assertEqual(response.status, 200)
            result = json.load(response)
            enqueue.assert_not_called()
        self.assertFalse(result['ok'])
        self.assertIn('exceeds LRECL', result['error'])

    def test_valid_custom_input_is_queued(self):
        directory = ROOT / 'fixtures/interest/i01-baseline/input'
        inputs = {name: (directory / name).read_text(encoding='latin-1')
                  for name in self.server.metadata['datasets']}
        with patch('lab.server.QUEUE.put') as enqueue, urlopen(self.custom_request(inputs)) as response:
            self.assertEqual(response.status, 202)
            result = json.load(response)
            enqueue.assert_called_once()
        self.assertEqual(result['status'], 'queued')
        self.assertEqual(len(result['id']), 32)

    def test_evidence_cannot_escape_repo_or_mount(self):
        with self.assertRaises(ValueError):
            safe_file('../outside-repository')
        for path in ['/evidence/../../etc/passwd', '/evidence/Makefile']:
            with self.assertRaises(HTTPError) as error:
                urlopen(self.url + path)
            self.assertEqual(error.exception.code, 404)

    def test_register_has_anchor_and_status(self):
        text = register_page()
        self.assertIn('id="INT-Q01"', text)
        self.assertIn('<th>Status</th>', text)
        self.assertIn('tr:target', text)


if __name__ == '__main__':
    unittest.main()
