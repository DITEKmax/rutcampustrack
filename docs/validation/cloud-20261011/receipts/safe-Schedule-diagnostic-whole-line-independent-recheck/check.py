"""Narrow readonly seals/delta and complete-line parser RAM controls; no target imports."""
import ast,copy,hashlib,json,re,stat,zipfile
from pathlib import Path,PurePosixPath
from xml.etree import ElementTree as ET
HERE=Path(__file__).parent
OLD=HERE.parent/'schedule_safe_fail_diagnostics_source'
NEW=HERE.parent/'schedule_safe_fail_diagnostics_line_correction'
checks=[]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert sha(OLD/'manifest.safe.json')=='9cafda102559d0f2b062fc0bfda816611d331121cee8e5c75a3412c761a0d3c9'
assert sha(OLD/'safe-source-package.zip')=='1628de2fef1f4d3eaadcd9444dc3fe75155bb3a1f9301e497d55deb3d68a59b8'
assert sha(NEW/'manifest.safe.json')=='50444d54dd0d5f117620ca00999366ab0f6ea60a631844849d35b4b31bac3e41'
old=json.loads((OLD/'manifest.safe.json').read_text());new=json.loads((NEW/'manifest.safe.json').read_text())
for m in [old,new]:
 for r in m['files']:assert sha(r['path'])==r['sha256'] and Path(r['path']).stat().st_size==r['bytes']
checks.append('original13+two_seals_unchanged_NEW11_hashes_exact')
zpath=NEW/'safe-source-correction.zip'
assert sha(zpath)=='f03d0e47d15521ad0aea3379877f1c09677200d27b357a6206a2670d667a4206'
payload={r['relative_path']:Path(r['path']).read_bytes() for r in new['files']};payload['manifest.safe.json']=(NEW/'manifest.safe.json').read_bytes()
with zipfile.ZipFile(zpath) as z:
 assert len(z.infolist())==len(set(z.namelist()))==12 and z.testzip() is None
 for info in z.infolist():
  p=PurePosixPath(info.filename);assert not p.is_absolute() and '..' not in p.parts and str(p)==info.filename
  assert stat.S_IFMT(info.external_attr>>16) in (0,stat.S_IFREG) and z.read(info)==payload[info.filename]
checks.append('NEW_ZIP12_CRC_unique_canonical_exact')
oldpaths={r['repo_path']:Path(r['path']) for r in old['files'] if r['kind']=='ASSIGNED_SOURCE'}
newpaths={r['repo_path']:Path(r['path']) for r in new['files'] if r['kind']=='ASSIGNED_SOURCE'}
for p in oldpaths:
 if p not in ['scripts/collect-ci-test-outcomes.py','tests/harness/test_ci_test_outcomes.py']:assert oldpaths[p].read_bytes()==newpaths[p].read_bytes()
checks.append('Java_and_workflow_BYTE_IDENTICAL_original_full_review_inherited')
a=ast.parse(oldpaths['scripts/collect-ci-test-outcomes.py'].read_text());b=ast.parse(newpaths['scripts/collect-ci-test-outcomes.py'].read_text())
oldfn=next(n for n in a.body if isinstance(n,ast.FunctionDef) and n.name=='cap_failure_diagnostic')
newfn=next(n for n in b.body if isinstance(n,ast.FunctionDef) and n.name=='cap_failure_diagnostic')
replacement=copy.deepcopy(b);replacement.body[replacement.body.index(next(n for n in replacement.body if isinstance(n,ast.FunctionDef) and n.name=='cap_failure_diagnostic'))]=oldfn
assert ast.dump(a)==ast.dump(replacement)
checks.append('collector_AST_outside_one_function_exact_all_whitelists_case_scope_bounds_intact')
ta=ast.parse(oldpaths['tests/harness/test_ci_test_outcomes.py'].read_text());tb=ast.parse(newpaths['tests/harness/test_ci_test_outcomes.py'].read_text())
added={'test_cap_marker_cutoff_never_attests_a_truncated_original_line','test_cap_marker_whole_bounded_lines_eof_newline_and_boundary_remain_valid'}
for node in tb.body:
 if isinstance(node,ast.ClassDef):node.body=[x for x in node.body if not isinstance(x,ast.FunctionDef) or x.name not in added]
assert ast.dump(ta)==ast.dump(tb)
checks.append('original18_harness_controls_AST_exact_only2_new_meaningful_regressions')
def parser(tree):
 names={'CAP_FAILURE_CLASSES','CAP_SQL_STATES','CAP_MARKER'}
 selected=[n for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in names for t in n.targets) or isinstance(n,ast.FunctionDef) and n.name=='cap_failure_diagnostic']
 env={'re':re,'ElementTree':ET};exec(compile(ast.Module(body=selected,type_ignores=[]),'<selected pure complete-line parser>','exec'),env);return env
o=parser(a);n=parser(b)
good='CAP_FUTURE_DIAGNOSTIC_V1 class=AssertionError sqlState=NONE barrier=TIMED_OUT'
assert len(good)==77
secret='SYNTHETIC_PRIVATE_TAIL_DO_NOT_OUTPUT'
pad=' '*(2048-len(good))+good
def call(env,text,message=''):
 c=ET.Element('testcase');f=ET.SubElement(c,'failure',message=message);f.text=text
 r=env['cap_failure_diagnostic'](c);assert secret not in json.dumps(r);return r
bad=pad+secret
assert o['CAP_MARKER'].fullmatch(bad.strip()) is None
assert call(o,bad)['state']=='CLOSED_MARKER' and call(n,bad)['stage']=='UNKNOWN'
checks.append('ORIGINAL_exact_cutoff_false_marker_RED_to_NEW_UNKNOWN_no_leak')
negatives=[pad+secret,pad+' '*50+secret,good+' '*(2048-len(good))+secret,'header\n'+' '*(2048-len(good)-len('header\n'))+good+secret,pad+secret+'\n',pad+'x\n', 'head\nhead\nhead\n'+good, ' '*(2049-len(good))+good+'\n']
for index,text in enumerate(negatives):assert call(n,text)=={'state':'ABSENT_OR_UNRECOGNIZED','stage':'UNKNOWN'}
checks.append('8_cutoff_overbudget_fourthline_negatives_UNKNOWN')
positives=[good,good+'\n'+secret,'java.lang.AssertionError:\n'+good+'\n'+secret,pad,pad+'\n'+secret,good+'\r\n'+secret,'one\ntwo\n'+good,good+'\r','\n'+good+'\n']
for index,text in enumerate(positives):assert call(n,text)['state']=='CLOSED_MARKER' and call(n,text)['stage']=='AWAIT_BARRIER'
checks.append('9_complete_EOF_LF_CRLF_header_exact2048_3rdline_positives')
assert call(n,secret,good)['state']=='CLOSED_MARKER'
assert call(n,secret,good+secret)['stage']=='UNKNOWN'
assert call(n,good.replace('TIMED_OUT','INTERRUPTED'),good)['stage']=='UNKNOWN'
checks.append('whole_message_positive_malformed_and_conflicting_complete_marker_negatives')
# At LF index2048 one-character lookahead is permitted; at2049 it is rejected.
assert call(n,pad+'\n'+secret)['state']=='CLOSED_MARKER'
assert call(n,' '+pad+'\n'+secret)['stage']=='UNKNOWN'
checks.append('LF_separator_lookahead2048_positive_2049_negative')
result={'verdict':'APPROVE_SOURCE','finding':'SCD-MARKER-CUTOFF-001 CLOSED_SOURCE','original_review_manifest_sha256':'52a841257fbefa25fa057ef2ac90f3bb85580554d4a5490bf0c3dd0de1c30079','original_source_manifest_sha256':sha(OLD/'manifest.safe.json'),'correction_manifest_sha256':sha(NEW/'manifest.safe.json'),'checks':checks,'checks_count':len(checks),'native_Java_DB_API_format_CI':'NOT_RUN','actual_XML_log_private_reads':0,'source_fix_or_target_import':False,'original_full_review_or_suite_replay':False,'historical96_CAP_cause':'UNKNOWN','selected_requested_model':'gpt-6.1-sol/high','actual_model_effort':'UNKNOWN'}
(HERE/'checks.safe.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'verdict':result['verdict'],'groups':len(checks),'native':False}))
