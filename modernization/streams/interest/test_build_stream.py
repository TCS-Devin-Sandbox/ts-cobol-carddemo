import json
import unittest
from unittest.mock import patch

import build_stream as stream


class StreamTest(unittest.TestCase):
    def test_manifest(self):
        m = stream.build()
        self.assertEqual(len(m['in_scope']['datasets']), 6)
        self.assertEqual(len(m['in_scope']['copybooks']), 5)
        self.assertEqual([x['name'] for x in m['in_scope']['jobs']], ['INTCALC'])
        self.assertEqual({x['module'] for x in m['module_coverage']}, set(stream.MODULES))
        self.assertTrue(any(x['name'] == 'COMBTRAN' and x['generation'] == '0'
                            for x in m['outside_dependencies'] if 'generation' in x))
        self.assertFalse(any(x.get('dd') == 'XREFFIL1' for x in m['edges']))
        raw = json.dumps(m, indent=2, ensure_ascii=False) + '\n'
        page = stream.render(m, raw)
        embedded = page.split('<script id="manifest" type="application/json">')[1].split('</script>')[0]
        self.assertEqual(json.loads(embedded), m)
        self.assertEqual(embedded, raw)

    def test_empty(self):
        self.assertEqual(stream.base(None), '')
        self.assertIn('<svg', stream.diagram([], 'empty'))

    def test_missing_source_evidence(self):
        with patch.object(stream, 'read', return_value='no token'):
            with self.assertRaises(StopIteration):
                stream.cite('absent', 'required')
        with self.assertRaises(AssertionError):
            stream.fact(evidence=[])


if __name__ == '__main__':
    unittest.main()
