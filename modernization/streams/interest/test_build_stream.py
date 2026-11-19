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
        path_edge = next(x for x in m['edges'] if x.get('dd') == 'XREFFIL1')
        self.assertEqual((path_edge['direction'], path_edge['status']), ('R', 'PROVEN'))
        self.assertIn('naming convention', path_edge['basis'])
        path_dataset = next(x for x in m['in_scope']['datasets'] if x['dd'] == 'XREFFIL1')
        self.assertEqual((path_dataset['direction'], path_dataset['record_layout']), ('R', 'CVACT03Y'))
        bindings = [x for x in m['edges'] if x['kind'] == 'load-binding']
        self.assertEqual(len(bindings), 2)
        self.assertEqual(bindings[0]['status'], 'PROVEN')
        self.assertEqual(bindings[0]['scope'], 'off-host loadlib')
        self.assertEqual(bindings[0]['evidence'], ['tools/build.sh:18-35'])
        self.assertEqual(bindings[1]['status'], 'CANDIDATE')
        self.assertEqual(bindings[1]['open_ask'], 'interest-4')
        self.assertIn('AWS.M2.CARDDEMO.LOADLIB', bindings[1]['source'])
        self.assertIn(bindings[1]['open_ask'], [a['id'] for a in m['open_asks']])
        self.assertEqual({k: m['in_scope']['programs'][0][k]
                          for k in ('file_name', 'program_id', 'load_module')},
                         dict(file_name='CBACT04C.cbl', program_id='CBACT04C', load_module='CBACT04C'))
        uncertainties = m['scheduler']['not_proven']
        self.assertEqual(len(uncertainties), 5)
        self.assertEqual(len({x['target'] for x in uncertainties}), 5)
        self.assertTrue(all(x['evidence'] for x in uncertainties))
        self.assertTrue(any(e['source'] == 'WAITSTEP' and e['target'] == 'OPENFIL'
                            and e['status'] == 'PROVEN' for e in m['scheduler']['edges']))
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
