#!/usr/bin/env python3
"""Rebuild the source-cited interest slice using only the Python standard library."""
import html
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
TABLE = 'MONTHLY-InterestCalculation'
SHA = '41519206b887cd0123ea850b46a96cdc9baa69af'
MODULES = ('AUTH', 'CARDDEMO', 'TRANTYPE', 'VSAMMQ')


def read(path):
    return (ROOT / path).read_text()


def load(path):
    return json.loads(read(path))


def cite(path, token):
    lines = read(path).splitlines()
    return f'{path}:{next(i for i, line in enumerate(lines, 1) if token in line)}'


def fact(**kwargs):
    assert kwargs.get('evidence'), kwargs
    return kwargs


def base(dsn):
    return re.sub(r'\([+-]?\d+\)$', '', dsn or '')


def direction(modes):
    return 'RW' if 'I-O' in modes else 'W' if any(x in modes for x in ('OUTPUT', 'EXTEND')) else 'R'


def estate_cics_files():
    bindings = {}
    for path in sorted((ROOT / 'app').rglob('*')):
        if path.suffix.lower() != '.csd':
            continue
        text = path.read_text()
        for block in re.finditer(r'\bDEFINE\s+FILE\(([^)]+)\)(.*?)(?=\bDEFINE\b|\Z)', text, re.I | re.S):
            dsn = re.search(r'\bDSNAME\(([^)]+)\)', block.group(2), re.I)
            if dsn:
                source = str(path.relative_to(ROOT)) + ':' + str(text[:block.start()].count('\n') + 1)
                bindings.setdefault(block.group(1).upper(), []).append({'attrs': {'DSNAME': dsn.group(1)}, 'source': source})
    return bindings


def build():
    conn = load('store/CARDDEMO/connections.json')
    jobs = load('store/CARDDEMO/jobs.json')['jobs']
    job = next(j for j in jobs if j['name'] == 'INTCALC')
    step = job['steps'][0]
    program = next(p for p in load('store/CARDDEMO/programs.json')['programs'] if p['id'] == 'CBACT04C')
    chain = next(c for c in load('store/CARDDEMO/chains.json')['chains'] if c['table'] == TABLE)
    notes = load('store/CARDDEMO/chainnotes.json')['tables'][TABLE]
    files = {f['dd']: f for f in conn['files'] if f['program'] == 'CBACT04C'}
    layouts = dict(zip(('TCATBALF', 'XREFFILE', 'DISCGRP', 'ACCTFILE', 'TRANSACT'), program['includes']))
    datasets, copies, edges = [], [], []
    for dd in step['dds']:
        if not dd['dsn'] or dd['name'] == 'STEPLIB':
            continue
        f = files.get(dd['name'])
        alternate_path = dd['name'] == 'XREFFIL1'
        if alternate_path:
            f = files['XREFFILE']
        definition = next((d for d in conn['datasets'] if base(d['dsn']) == base(dd['dsn'])), {})
        ev = [dd['source']]
        layout = layouts.get(dd['name'])
        if alternate_path:
            layout = layouts['XREFFILE']
            ev += ['app/cbl/CBACT04C.cbl:34-39', 'app/cbl/CBACT04C.cbl:393-395', 'app/jcl/XREFFILE.jcl:72-92']
        if f:
            ev += [f['source'], 'app/cbl/CBACT04C.cbl:61-92'] + [s['source'] for s in f['op_sites'] if s['op'] in ('OPEN', 'READ', 'WRITE', 'REWRITE')]
        item = fact(dsn=base(dd['dsn']), dd=dd['name'], disp=dd['disp'],
                    generation='+1' if '(+1)' in dd['dsn'] else None,
                    direction=direction(f['open_modes']) if f else 'NOT_OPENED',
                    organization='GDG' if '(+1)' in dd['dsn'] else 'AIX path' if 'PATH' in dd['dsn'] else 'KSDS',
                    record_length=f['record_length'] if f else 50,
                    record_layout=layout['name'] if layout else 'CVACT03Y (base record; path not opened)',
                    definition=definition.get('definition'), evidence=ev)
        if dd['name'] == 'TRANSACT':
            item['gdg_base'] = fact(name=base(dd['dsn']), limit=5, scratch=True,
                                    evidence=['app/jcl/DEFGDGB.jcl:48-53'])
        if definition.get('type_basis'):
            ev.append(definition['type_basis']['source'])
        if not f:
            ev += ['app/cbl/CBACT04C.cbl:27-56', 'app/jcl/XREFFILE.jcl:67-102', 'app/cpy/CVACT03Y.cpy:1-10']
        if layout:
            cp = 'app/cpy/' + layout['name'] + '.cpy'
            cev = [f"app/cbl/CBACT04C.cbl:{layout['line']}", cite(cp, '01 ')]
            item['evidence'] += cev
            if not alternate_path:
                copies.append(fact(name=layout['name'], path=cp, fd=f['file'], records=f['records'],
                                   placement='WORKING-STORAGE; moved to/from FD record, not an FD COPY',
                                   record_length=f['record_length'], evidence=cev + ev))
            d = item['direction']
            for mode in ('R', 'W'):
                if mode in d:
                    edges.append(fact(kind='data', source=item['dsn'] if mode == 'R' else 'CBACT04C',
                                      target='CBACT04C' if mode == 'R' else item['dsn'], direction=mode,
                                      dd=dd['name'], status='PROVEN', basis='IBM COBOL alternate-index DD naming convention: first seven characters of XREFFILE + 1 = XREFFIL1 for the first alternate key; READ KEY IS FD-XREF-ACCT-ID uses the AIX path' if alternate_path else 'SELECT binding and OPEN mode', evidence=list(ev)))
        datasets.append(item)
    edges += [fact(kind='exec', source='INTCALC.STEP15', target='load-module:CBACT04C', status='PROVEN',
                   evidence=[step['source']]),
              fact(kind='load-binding', source='load-module:CBACT04C', target='app/cbl/CBACT04C.cbl',
                   status='PROVEN', scope='off-host loadlib', basis='Harness compiles source basename into loadlib',
                   evidence=['tools/build.sh:18-35']),
              fact(kind='load-binding', source='AWS.M2.CARDDEMO.LOADLIB:CBACT04C', target='app/cbl/CBACT04C.cbl',
                   status='CANDIDATE', scope='production load module', open_ask='interest-4',
                   basis='names match (PGM=, file stem, PROGRAM-ID) but no binder map/link-edit listing supplied',
                   evidence=['app/jcl/INTCALC.jcl:22-23', 'app/cbl/CBACT04C.cbl:23']),
              fact(kind='call', source='CBACT04C', target='CEE3ABD', status='PROVEN',
                   basis='Static literal call; runtime implementation not assumed', evidence=['app/cbl/CBACT04C.cbl:632'])]
    setup = []
    for name in load('tools/runjcl/chains.json')['interest']['setup']:
        j = next(j for j in jobs if j['name'] == name)
        setup.append(fact(job=name, role='setup', purpose='Defines GDG bases with LIMIT(5) SCRATCH' if name == 'DEFGDGB' else
                          'Deletes/redefines VSAM and REPRO loads seed records' + ('; defines and builds alternate index/path' if name == 'XREFFILE' else ''),
                          steps=[s['step'] for s in j['steps']], evidence=['tools/runjcl/chains.json:52-58'] + [s['source'] for s in j['steps']]))
    scope_ds = {d['dsn'] for d in datasets}
    outside = []

    def add(module, kind, name, dsn, dr, status, evidence, **extra):
        outside.append(fact(module=module, kind=kind, name=name, dataset=base(dsn), direction=dr,
                            status=status, evidence=sorted(set(e for e in evidence if e)), **extra))

    coverage = []
    estate_files = estate_cics_files()
    for module in MODULES:
        prefix = 'store/' + module + '/'
        c = load(prefix + 'connections.json')
        js = load(prefix + 'jobs.json')
        online = load(prefix + 'online.json')
        coverage.append(fact(module=module, datasets_examined=len(c['datasets']), jobs_examined=len(js['jobs']),
                             transactions_examined=len(online['transactions']),
                             evidence=[prefix + f + ':1' for f in ('connections.json', 'jobs.json', 'online.json')]))
        for f in c['files']:
            if f['program'] == 'CBACT04C':
                continue
            for b in f['bindings']:
                if base(b['dsn']) in scope_ds:
                    add(module, 'program', f['program'], b['dsn'], direction(f['open_modes']),
                        'CANDIDATE' if b.get('dynamic_site') or not f['open_modes'] else 'PROVEN',
                        [f['source'], b['source']] + [s['source'] for s in f.get('op_sites', []) if s['op'] == 'OPEN'])
        for ds in c['datasets']:
            if base(ds['dsn']) not in scope_ds:
                continue
            definition = ds.get('definition') or {}
            if definition.get('job'):
                add(module, 'job', definition['job'], ds['dsn'], 'W', 'PROVEN',
                    [definition['source']], step=definition['step'], basis='IDCAMS definition; setup/administration, not stream logic')
            for deletion in ds.get('deleted_by', []):
                add(module, 'job', deletion['job'], ds['dsn'], 'W', 'PROVEN',
                    [deletion['source']], step=deletion['step'], basis='IDCAMS deletion; destructive setup/administration')
            for s in ds['steps']:
                if s['job'] == 'INTCALC':
                    continue
                bound = [f for f in c['files'] for b in f['bindings'] if b['job'] == s['job'] and b['step'] == s['step'] and b['dd'] == s['dd']]
                utility = [u for u in ds.get('utility_use', []) if u['job'] == s['job'] and u['step'] == s['step']]
                dr = direction(bound[0]['open_modes']) if bound else 'W' if any(u['role'] == 'repro-to' for u in utility) else 'R' if utility else 'RW'
                proven = bool(bound and bound[0]['open_modes']) or bool(utility)
                add(module, 'job', s['job'], ds['dsn'], dr, 'PROVEN' if proven else 'CANDIDATE',
                    [s['source']] + [u['source'] for u in utility] + ([bound[0]['source']] if bound else []),
                    step=s['step'], basis='OPEN/utility evidence' if proven else 'Allocation only; R/W possibilities, execution and direction not proven')
        for resource in c['cics']['files']:
            local_csd = resource.get('csd') or {}
            bindings = {b['source']: b for b in estate_files.get(resource['name'], [])}
            if local_csd.get('attrs', {}).get('DSNAME'):
                bindings[local_csd['source']] = local_csd
            for csd in bindings.values():
                dsn = csd['attrs']['DSNAME']
                if base(dsn) not in scope_ds:
                    continue
                foreign_binding = csd['source'] != local_csd.get('source')
                binding_basis = 'CSD binding comes from another module: ' + csd['source'] + '; deployment in this module region is unproven' if foreign_binding else 'Module CSD binding: ' + csd['source']
                for u in resource['uses']:
                    dr = 'W' if u['command'] in ('WRITE', 'REWRITE', 'DELETE') else 'R'
                    add(module, 'CICS program', u['program'], dsn, dr,
                        'PROVEN' if not foreign_binding and u.get('resolution') == 'constant' else 'CANDIDATE',
                        [u['source'], csd['source']], command=u['command'], basis=binding_basis)
                    for tx in online['transactions']:
                        reach = next((p for p in tx['programs'] if p['id'] == u['program']), None)
                        if reach:
                            add(module, 'CICS transaction', tx['transid'], dsn, dr,
                                'PROVEN' if not foreign_binding and reach['certainty'] == 'proven' else 'CANDIDATE',
                                [u['source'], csd['source'], tx['definition'].get('source'), reach.get('source')],
                                via=u['program'], basis=binding_basis + '; entry/closure certainty is not proof of runtime overlap')
    add('CARDDEMO', 'job', 'COMBTRAN', 'AWS.M2.CARDDEMO.SYSTRAN', 'R', 'PROVEN',
        ['app/jcl/COMBTRAN.jcl:21-30', 'app/scheduler/CardDemo.controlm:72-78'], step='STEP05R', generation='0', basis='Concatenated SORTIN; GDG roll from prior job (+1)')
    proven_jobs = {(x['module'], x['name'], x.get('step'), x['dataset']) for x in outside
                   if x['kind'] == 'job' and x['status'] == 'PROVEN'}
    outside = [x for x in outside if not (x['kind'] == 'job' and x['status'] == 'CANDIDATE'
               and (x['module'], x['name'], x.get('step'), x['dataset']) in proven_jobs)]
    outside = sorted({json.dumps(x, sort_keys=True): x for x in outside}.values(), key=lambda x: (x['module'], x['kind'], x['name'], x['dataset'], x['direction'], str(x['evidence'])))
    sched_path = 'app/scheduler/CardDemo.controlm'
    folder = next(f for f in ET.fromstring(read(sched_path)).iter('FOLDER') if f.attrib['FOLDER_NAME'] == TABLE)
    sjobs, order = [], []
    for j in folder.findall('JOB'):
        name = j.attrib['JOBNAME']
        sjobs.append(fact(job=name, role='stream' if name == 'INTCALC' else 'scheduler neighbour',
                           jobisn=j.attrib['JOBISN'], maxrerun=j.attrib['MAXRERUN'],
                           incond=[x.attrib for x in j.findall('INCOND')], outcond=[x.attrib for x in j.findall('OUTCOND')],
                           evidence=[cite(sched_path, 'DESCRIPTION="MONTHLY ' + name + '"')]))
    for a, b in zip(sjobs, sjobs[1:]):
        token = TABLE + '-' + a['job']
        assert any(x['NAME'] == token and x['SIGN'] == '+' for x in a['outcond'])
        assert any(x['NAME'] == token for x in b['incond'])
        order.append(fact(source=a['job'], target=b['job'], condition=token, status='PROVEN',
                          evidence=[cite(sched_path, '<OUTCOND NAME="' + token + '" ODATE="ODAT" SIGN="+"'),
                                    cite(sched_path, '<INCOND NAME="' + token + '"')]))
    issues = [fact(topic='Concatenated SORTIN dropped', detail='mftb reports SYSTRAN unconsumed; COMBTRAN.STEP05R reads it by GDG roll. Keep scanner JSON intact.',
                   evidence=['app/jcl/COMBTRAN.jcl:23-26', 'store/CARDDEMO/chains.json:1'])]
    discrepancies = []
    for topic, mv, gv, decision, ev in [
        ('Stream versus folder', 'Five scheduled jobs', 'INTCALC plus setup jobs', 'Only INTCALC.STEP15 is stream logic; four neighbours remain outside.', ['app/scheduler/CardDemo.controlm:64-92','tools/runjcl/chains.json:43-62']),
        ('SYSTRAN consumer', 'chain-output-unconsumed (closed by skill)', 'Output only', 'COMBTRAN.STEP05R reads SYSTRAN(0), rolling the previous job (+1).', ['app/jcl/COMBTRAN.jcl:23-26','app/jcl/INTCALC.jcl:37-40']),
        ('Data directions', 'OPEN-mode directions, with DISP fallback', 'Undirected dotted data edges', 'ACCTFILE RW; TCATBALF/XREFFILE/DISCGRP R; TRANSACT W.', ['app/cbl/CBACT04C.cbl:234-309','docs/chain-graph.md:112-119']),
        ('XREFFIL1', 'DISP SHR fallback read', 'DD allocation view', 'Read via AIX path for the first alternate key; IBM COBOL naming convention binds XREFFILE to XREFFIL1.', ['app/jcl/INTCALC.jcl:31-32','app/cbl/CBACT04C.cbl:34-39','app/cbl/CBACT04C.cbl:393-395','app/jcl/XREFFILE.jcl:72-92']),
        ('Cycle date', 'Literal PARM', 'No date semantics', '2022071800 fixed, used in transaction ID; replacement policy unknown.', ['app/jcl/INTCALC.jcl:22','app/cbl/CBACT04C.cbl:476-480']),
        ('Restart', 'MAXRERUN=5 resubmission intent', 'No restart guarantee', 'Partial update recovery not proven safe.', ['app/scheduler/CardDemo.controlm:70','app/cbl/CBACT04C.cbl:350-356']),
        ('CA7 coverage', 'CA7 CARDDEMO is separate from monthly folder', 'Harness is scheduler-independent', 'CA7 lists CLOSEFIL/WAITSTEP/OPENFIL; no INTCALC or COMBTRAN in supplied export.', ['app/scheduler/CardDemo.ca7:1-571']),
        ('Fee and EOF semantics', 'Descriptions mention interest and fees', 'Names interest calculation', 'Fee paragraph is EXIT only; no final-account update at EOF; preserve legacy semantics.', ['app/cbl/CBACT04C.cbl:188-227','app/cbl/CBACT04C.cbl:518-520'])]:
        discrepancies.append(fact(topic=topic,mftb_view=mv,chain_graph_view=gv,source_decision=decision,evidence=ev))
    asks = [fact(id='interest-'+str(i), ask=a['request'], owner='Operations/application' if i == 1 else 'Scheduler/application' if i == 2 else 'Scheduler/CICS',
                 detail=a['message'], evidence=a['evidence']) for i,a in enumerate(notes['asks'],1)]
    asks.append(fact(id='interest-4',ask='Supply production binder/load-library provenance; off-host build does not prove deployed module identity.',owner='Build/release',evidence=['tools/build.sh:18-35','app/jcl/INTCALC.jcl:22-23']))
    m = dict(baseline=fact(branch='demo/01-intake',sha=SHA,evidence=['modernization/INTAKE.md:1']),
             root=fact(path=job['path'],evidence=[step['source']]), scheduler_folder=fact(name=TABLE,evidence=[sched_path+':64']),
             in_scope=dict(jobs=[fact(name='INTCALC',evidence=[step['source']])],
                           steps=[fact(name='STEP15',parm=step['parm'],evidence=[step['source']])],
                           programs=[fact(file_name='CBACT04C.cbl',program_id='CBACT04C',load_module='CBACT04C',
                                          binding='Off-host build proven; deployed z/OS load binding unverified',evidence=['app/cbl/CBACT04C.cbl:23','tools/build.sh:18-35'])],
                           copybooks=copies,datasets=datasets),edges=edges, setup_jobs=setup,outside_dependencies=outside,
             module_coverage=coverage,scheduler=dict(jobs=sjobs,edges=order,
             not_proven=[
                 fact(source='INTCALC', target='POSTTRAN / daily CARDDEMO table',
                      detail='Ordering on shared ACCTDATA and TCATBALF is not proven between the monthly folder and the separate CA7 CARDDEMO table.',
                      evidence=[sched_path+':64-92', 'app/scheduler/CardDemo.ca7:70-97', 'app/jcl/POSTTRAN.jcl:39-42', 'app/jcl/INTCALC.jcl:27-34']),
                 fact(source='INTCALC', target='CICS online updates',
                      detail='Online update exclusion is not proven: CLOSEFIL/OPENFIL SDSF runtime effects are opaque; scheduled close/open names do not prove quiescence.',
                      evidence=['app/jcl/CLOSEFIL.jcl:22-32', 'app/jcl/OPENFIL.jcl:22-32', 'app/csd/CARDDEMO.CSD:1-17', 'app/cbl/COACTUPC.cbl:4065', sched_path+':64-92']),
                 fact(source='WAITSTEP', target='OPENFIL',
                      detail='Both have JOBISN=4. WAITSTEP OUTCOND and OPENFIL INCOND prove a condition edge; this is the only explicit ordering mechanism between them in the supplied folder. Import/runtime behavior with duplicate identifiers remains unproven.',
                      evidence=[sched_path+':81-90']),
                 fact(source='INTCALC', target='DisclosureGroupsRefresh.DISCGRP',
                      detail='Ordering is not proven between INTCALC reading DISCGRP and DisclosureGroupsRefresh deleting, redefining and loading it; the weekly folder conditions do not name the monthly folder.',
                      evidence=[sched_path+':32-56', sched_path+':64-92', 'app/jcl/DISCGRP.jcl:25-26', 'app/jcl/DISCGRP.jcl:36-49', 'app/jcl/DISCGRP.jcl:54-61', 'app/jcl/INTCALC.jcl:35-36', 'app/cbl/CBACT04C.cbl:272']),
                 fact(source=TABLE, target='Other scheduler folders/tables',
                      detail='No cross-folder trigger into or out of the monthly folder appears in the supplied Control-M export: its INCOND/OUTCOND names are internal. External production triggers or cross-scheduler dependencies are not proven absent.',
                      evidence=[sched_path+':1-93', 'app/scheduler/CardDemo.ca7:1-571'])
             ],
             neighbour_calls=[fact(source='COBSWAIT',target='MVSWAIT',status='PROVEN',in_scope=False,evidence=['app/cbl/COBSWAIT.cbl:38','app/asm/MVSWAIT.asm:17-27'])]),
             discrepancies=discrepancies,toolbox_issues=issues,open_asks=asks,
             scanner_summary=fact(detail=f"Monthly scan has {len(chain['jobs'])} jobs; source decisions above do not modify scanner facts.",evidence=['store/CARDDEMO/chains.json:1']))
    counts = {}

    def count_lists(obj, prefix=''):
        if isinstance(obj,dict):
            for k,v in obj.items():
                count_lists(v, prefix+'.'+k if prefix else k)
        elif isinstance(obj,list):
            counts[prefix] = len(obj)
    count_lists(m)
    m['counts'] = counts
    return m


def diagram(labels, title, dashed=False):
    width = max(720, len(labels)*180)
    nodes = []
    for i,label in enumerate(labels):
        x = 15+i*180
        nodes.append(f'<rect x="{x}" y="35" width="155" height="48" rx="8"/><text x="{x+77}" y="64" text-anchor="middle">{html.escape(label)}</text>')
        if i:
            nodes.append(f'<path d="M{x-25} 59 H{x}" marker-end="url(#arrow-{title})" '+('stroke-dasharray="5 4"' if dashed else '')+'/>')
    return f'<svg role="img" aria-label="{title}" viewBox="0 0 {width} 110"><defs><marker id="arrow-{title}" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0 0 L7 3 L0 6 Z"/></marker></defs>'+''.join(nodes)+'</svg>'


def render(m, raw):
    tabs = [('overview','Overview'),('in_scope','In scope'),('edges','Edges'),('setup_jobs','Setup'),('outside_dependencies','Outside dependencies'),('scheduler','Scheduler'),('discrepancies','Discrepancies'),('open_asks','Open asks')]
    sections = []
    for key,title in tabs:
        body = ''
        if key == 'overview':
            body = '<p>One stream: INTCALC → STEP15 → CBACT04C. Setup and four scheduler neighbours are not stream logic. COMBTRAN is an outside-stream consumer. Posting is outside scope.</p><a href="#open_asks">Review unresolved evidence requests</a><h3>Scheduler order · solid = PROVEN</h3>'+diagram([j['job'] for j in m['scheduler']['jobs']], 'scheduler')
            body += '<h3>Data flow · solid = PROVEN / dashed = CANDIDATE</h3>'
            for d in m['in_scope']['datasets']:
                if d['direction'] != 'NOT_OPENED':
                    labels = [d['dd'], 'CBACT04C'] if d['direction'] == 'R' else ['CBACT04C', d['dd']]
                    body += diagram(labels, 'data-'+d['dd'])
                    if d['direction']=='RW':
                        body += diagram([d['dd'],'CBACT04C'], 'read-'+d['dd'])
            body += '<div id="counts"></div><details><summary>Toolbox issues and module coverage</summary><pre>'+html.escape(json.dumps({'toolbox_issues':m['toolbox_issues'],'module_coverage':m['module_coverage']},indent=2))+'</pre></details>'
        elif key in ('edges','outside_dependencies'):
            body = f'<label>Status <select id="{key}-status"><option>ALL</option><option>PROVEN</option><option>CANDIDATE</option></select></label> <label>Text <input type="search" id="{key}-search"></label><p aria-live="polite" id="{key}-result"></p><div class="scroll"><table id="{key}-table"><thead><tr><th>Status</th><th>Fact</th><th>Evidence</th></tr></thead><tbody></tbody></table></div>'
        else:
            value = m[key]
            body = '<details open><summary>Cited '+title.lower()+'</summary><pre>'+html.escape(json.dumps(value,indent=2))+'</pre></details>'
        sections.append(f'<section role="tabpanel" aria-labelledby="tab-{key}" id="{key}"'+('' if key=='overview' else ' hidden')+f'><h2>{title}</h2>{body}</section>')
    return '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>INTCALC · Interest stream</title><style>
    :root{font:16px system-ui;color:#e7edf7;background:#0c1424}body{max-width:1250px;margin:auto;padding:32px}h1{font-size:38px;margin-bottom:8px}h2{color:#84d8cd}header{border-left:5px solid #84d8cd;padding-left:24px;margin-bottom:28px}code{overflow-wrap:anywhere}button,input,select{font:inherit;padding:9px;border-radius:6px;border:1px solid #52627b;background:#14243a;color:inherit}button[aria-selected=true]{background:#84d8cd;color:#102030}button:focus-visible,a:focus-visible{outline:3px solid #f4bd61}nav{display:flex;flex-wrap:wrap;gap:8px}section{margin-top:24px;padding:24px;background:#142035;border-radius:12px}a{color:#84d8cd}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.6 monospace}details{margin:20px 0}summary{cursor:pointer}table{border-collapse:collapse;width:100%;font-size:13px}td,th{padding:12px;text-align:left;border-bottom:1px solid #344259;vertical-align:top}td{overflow-wrap:anywhere;max-width:700px}.scroll{overflow:auto}svg{display:block;width:100%;max-height:125px}svg rect{fill:#203c51;stroke:#84d8cd}svg text{fill:#e7edf7;font:14px system-ui}svg path{stroke:#84d8cd;fill:none;stroke-width:2}svg marker path{fill:#84d8cd}#counts{display:flex;gap:10px;flex-wrap:wrap}.count{padding:10px;background:#203047;border-radius:5px}.muted{color:#aab9cd}
    </style><header><p class="muted">LEGACY ESTATE / STREAM 02</p><h1>Monthly interest · INTCALC</h1><p>Baseline demo/01-intake<br><code>'''+SHA+'''</code></p><p>Evidence-led scope, not a migration design. Static proof is not runtime assurance.</p></header><nav role="tablist" aria-label="Stream views">'''+''.join(f'<button role="tab" id="tab-{k}" aria-controls="{k}" aria-selected="{str(i==0).lower()}" tabindex="{0 if i==0 else -1}">{t}</button>' for i,(k,t) in enumerate(tabs))+'''</nav>'''+''.join(sections)+'''<script id="manifest" type="application/json">'''+raw.replace('</','<' + chr(92) + '/')+r'''</script><script>
    const M=JSON.parse(document.getElementById('manifest').textContent);
    const tabs=[...document.querySelectorAll('[role=tab]')];
    function activate(t){tabs.forEach(b=>{const on=b===t;b.setAttribute('aria-selected',on);b.tabIndex=on?0:-1;document.getElementById(b.getAttribute('aria-controls')).hidden=!on;});}
    function syncHash(){let id='';try{id=decodeURIComponent(location.hash.slice(1));}catch{}const target=document.getElementById(id),panel=target?.closest('[role=tabpanel]');const tab=tabs.find(b=>b.getAttribute('aria-controls')===panel?.id)||tabs[0];activate(tab);if(panel&&target!==panel){for(let p=target.parentElement;p&&p!==panel;p=p.parentElement){if(p.tagName==='DETAILS')p.open=true;}target.scrollIntoView();}}
    function selectTab(tab){const hash='#'+tab.getAttribute('aria-controls');if(location.hash!==hash)history.pushState(null,'',hash);activate(tab);}
    document.querySelectorAll('a[href^="#"]').forEach(a=>a.onclick=e=>{e.preventDefault();if(location.hash!==a.hash)history.pushState(null,'',a.hash);syncHash();});
    tabs.forEach((b,i)=>{b.onclick=()=>selectTab(b);b.onkeydown=e=>{let n;if(e.key==='ArrowRight')n=(i+1)%tabs.length;if(e.key==='ArrowLeft')n=(i+tabs.length-1)%tabs.length;if(e.key==='Home')n=0;if(e.key==='End')n=tabs.length-1;if(n!==undefined){e.preventDefault();selectTab(tabs[n]);tabs[n].focus();}};});
    window.addEventListener('hashchange',syncHash);
    window.addEventListener('popstate',syncHash);
    Object.entries(M.counts).filter(([k])=>!k.includes('evidence')&&!/\.\d/.test(k)).forEach(([k,n])=>{const e=document.createElement('span');e.className='count';e.dataset.count=n;e.dataset.countKey=k;e.textContent=k+': '+n;document.getElementById('counts').append(e);});
    for(const key of ['edges','outside_dependencies']){const status=document.getElementById(key+'-status'),search=document.getElementById(key+'-search'),tbody=document.querySelector('#'+key+'-table tbody');function draw(){tbody.replaceChildren();const rows=M[key].filter(r=>(status.value==='ALL'||r.status===status.value)&&JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()));for(const r of rows){const tr=document.createElement('tr');for(const v of [r.status,JSON.stringify(Object.fromEntries(Object.entries(r).filter(([k])=>!['status','evidence'].includes(k)))),r.evidence.join('\n')]){const td=document.createElement('td');td.textContent=v;tr.append(td);}tbody.append(tr);}const result=document.getElementById(key+'-result');result.textContent=rows.length+' matching records';result.dataset.count=rows.length;}status.onchange=draw;search.oninput=draw;draw();}
    syncHash();
    </script></html>'''


if __name__ == '__main__':
    manifest = build()
    raw = json.dumps(manifest, indent=2, ensure_ascii=False) + '\n'
    (OUT / 'manifest.json').write_text(raw)
    (OUT / 'stream-INTCALC.html').write_text(render(manifest, raw))
    print('Generated manifest.json and stream-INTCALC.html')
