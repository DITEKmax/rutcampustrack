from pathlib import Path
import json,re,hashlib,subprocess,difflib,collections
repo=Path('/workspace/rutcampustrack'); out=Path('/workspace/scratch/rct-validation/source_manifest_recovery')
original=(repo/'docs/sources/manifest.yaml').read_bytes(); raw=original.decode('utf-8'); map_bytes=(repo/'docs/sources/address-map.json').read_bytes(); amap=json.loads(map_bytes)['entries']; by_source={e['source']:i for i,e in enumerate(amap)}
def strict(pairs):
 d={}
 for k,v in pairs:
  if k in d:raise ValueError('duplicate key '+k)
  d[k]=v
 return d
start=raw.index('    "entries":  ['); end=raw.index('    "post_transfer_adaptations":'); blocks=[]
for m in re.finditer(r'^                    \{\n.*?^                    \}',raw[start:end],re.M|re.S):
 try: record=json.loads(m.group(),object_pairs_hook=strict); error=None
 except ValueError as exc: record=None;error=str(exc)
 line=raw[:start+m.start()].count('\n')+1
 source=re.search(r'"source":\s*"([^"]+)"',m.group()).group(1)
 if record and source in by_source:
  mism=[k for k,v in amap[by_source[source]].items() if record.get(k)!=v]
  if set(mism)=={'source_path','target','canonical_path','inventory_status','status'}:error='truncated fields';record=None
 blocks.append(dict(start=start+m.start(),end=start+m.end(),line=line,source=source,record=record,error=error))
assert len(blocks)==2030
patches=[]; affected=[]; schema=set(blocks[0]['record'])
for n,b in enumerate(blocks):
 if not b['error']:continue
 first=by_source[b['source']];last=by_source[blocks[n+1]['source']]-1; recovered=[]
 fragment=raw[b['start']:b['end']]
 surviving=re.findall(r'"(source_sha256|verified_source_sha256|result_sha256)":\s*"([0-9a-f]{64})"',fragment)
 hashes_by_source={}
 for candidate in amap[first:last+1]:
  canonical=repo/candidate['canonical_path']
  if canonical.is_file(): hashes_by_source[candidate['source']]=hashlib.sha256(canonical.read_bytes()).hexdigest()
 assert first<=last
 for idx in range(first,last+1):
  entry=dict(amap[idx]);missing=sorted(schema-set(entry))
  entry['source_sha256']=None;entry['verified_source_sha256']=None;entry['result_sha256']=None
  restored_fields=[]
  for field,sha in surviving:
   matching=[source for source,observed in hashes_by_source.items() if observed==sha]
   if matching==[entry['source']]:
    entry[field]=sha;restored_fields.append(field)
  entry['recovery']={'date':'2026-10-11','state':'partial-metadata-recovery','evidence':'docs/sources/address-map.json','evidence_sha256':hashlib.sha256(map_bytes).hexdigest(),'damaged_manifest_line':b['line'],'fields_not_recovered_from_address_map':missing,'checksum_fields_restored_from_surviving_fragment':sorted(set(restored_fields)),'note':'Identity, addresses, inventory/status and historical verification claim recovered from the intact address map. Surviving checksum fields are restored only when exactly one canonical file in this damaged range matches the full original checksum. Null checksums do not claim original byte identity. Owner/date/category/transfer fields are not reconstructed from neighboring entries; surviving fragments and original corrupt bytes are preserved separately.'}
  recovered.append(entry); affected.append({**amap[idx],'address_map_index':idx,'damaged_manifest_line':b['line'],'missing_manifest_fields':missing})
 def fmt(e):return '\n'.join('                    '+line for line in json.dumps(e,ensure_ascii=False,indent=4).splitlines())
 replacement=',\n'.join(fmt(e) for e in recovered)
 patches.append({**b,'first_index':first,'last_index':last,'replacement':replacement,'count':len(recovered)})
proposed=raw
for p in reversed(patches):proposed=proposed[:p['start']]+p['replacement']+proposed[p['end']:]
parsed=json.loads(proposed,object_pairs_hook=strict)
assert len(parsed['entries'])==2240
assert {e['source'] for e in amap}.issubset({e['source'] for e in parsed['entries']})
assert len(affected)==219
# Every unaffected original record occurs with identical bytes; all non-entry bytes stay unchanged.
for b in blocks:
 if not b['error']:assert raw[b['start']:b['end']] in proposed
assert raw[:start]==proposed[:start]
assert raw[end:]==proposed[proposed.index('    "post_transfer_adaptations":'):]
(out/'manifest.restored-proposal.yaml').write_bytes(proposed.encode())
(out/'restoration.patch').write_text(''.join(difflib.unified_diff(raw.splitlines(keepends=True),proposed.splitlines(keepends=True),fromfile='a/docs/sources/manifest.yaml',tofile='b/docs/sources/manifest.yaml')))
(out/'affected-source-records.json').write_text(json.dumps(affected,ensure_ascii=False,indent=2)+'\n')
validation={'baseline':'3908fad9624f97aec8891fc79338ceb6b0616bf3','original_sha256':hashlib.sha256(original).hexdigest(),'proposal_sha256':hashlib.sha256(proposed.encode()).hexdigest(),'address_map_sha256':hashlib.sha256(map_bytes).hexdigest(),'original_record_blocks':2030,'unchanged_record_blocks':2021,'restored_records':219,'proposed_entries':2240,'preserved_later_derived_entries':10,'duplicate_keys':'none','all_map_sources_present':True,'unchanged_header_and_footer':True,'regions':[{'original_line':p['line'],'first_index':p['first_index'],'last_index':p['last_index'],'count':p['count'],'cause':p['error']} for p in patches]}
(out/'proposal-validation.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(validation,ensure_ascii=False,indent=2))
