from pathlib import Path
import hashlib, json, re, stat

OWN=Path(__file__).parent
ROOT=Path('/workspace/rutcampustrack')
REL='services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java'
before=(ROOT/REL).read_text();after=(OWN/'source'/REL).read_text()
assert hashlib.sha256(before.encode()).hexdigest()=='4a9c9ac845d2fdf62b2b0f26497a897c42878eed8ca5c766e8747a6d00ef6ad0'
identifier=re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*')
number=re.compile(r'(?:0[xX][0-9a-fA-F_]+(?:\.[0-9a-fA-F_]*)?(?:[pP][+-]?[0-9_]+)?[fFdDlL]?|0[bB][01_]+[lL]?|(?:[0-9][0-9_]*(?:\.[0-9_]*)?|\.[0-9][0-9_]*)(?:[eE][+-]?[0-9_]+)?[fFdDlL]?)')
ops=sorted(['>>>=','<<=','>>=','>>>','<<','>>','==','!=','<=','>=','&&','||','++','--','+=','-=','*=','/=','&=','|=','^=','%=','->','::','...'],key=len,reverse=True)
punct=set('(){}[];,.@=><!~?:+-*/&|^%')
def lex(s):
 assert not re.search(r'\\u+?[0-9a-fA-F]{4}',s),'unsupported Unicode escape preprocessing'
 code=[];literals=[];comments=[];delimiters=[];stack=[];i=0
 while i<len(s):
  if s[i].isspace():i+=1;continue
  start=i
  if s.startswith('//',i):
   end=s.find('\n',i);i=len(s) if end<0 else end;comments.append(s[start:i]);continue
  if s.startswith('/*',i):
   end=s.find('*/',i+2);assert end>=0;i=end+2;comments.append(s[start:i]);continue
  if s.startswith('"""',i):
   end=s.find('"""',i+3)
   while end>=0:
    slash=end-1
    while slash>=start and s[slash]=='\\':slash-=1
    if (end-1-slash)%2==0:break
    end=s.find('"""',end+3)
   assert end>=0,'unterminated text block';i=end+3;code.append(s[start:i]);literals.append(s[start:i]);continue
  if s[i] in ['"',"'"]:
   quote=s[i];i+=1
   while i<len(s) and s[i]!=quote:
    assert s[i] not in '\r\n','unterminated literal'
    i+=2 if s[i]=='\\' else 1
   assert i<len(s) and s[i]==quote;i+=1;code.append(s[start:i]);literals.append(s[start:i]);continue
  match=identifier.match(s,i) or number.match(s,i)
  if match:i=match.end();code.append(match.group());continue
  op=next((o for o in ops if s.startswith(o,i)),None)
  if op:i+=len(op);code.append(op);continue
  assert s[i] in punct,'unsupported source character'
  ch=s[i];i+=1;code.append(ch)
  if ch in '({[':stack.append(ch);delimiters.append(ch)
  if ch in ')}]':assert stack and stack.pop()=={')':'(','}':'{',']':'['}[ch];delimiters.append(ch)
 assert not stack
 return {'code':code,'literals':literals,'comments':comments,'delimiters':delimiters}
a=lex(before);b=lex(after);assert a==b
codes=['40P01','55P03','40001','23502','23503','23505','23514','P0001']
start=before.index('                                    case "40P01",')
end=before.index('                                    default -> "OTHER";',start)
start_after=after.index('                                    case "40P01",')
end_after=after.index('                                    default -> "OTHER";',start_after)
assert before[:start]==after[:start_after] and before[end:]==after[end_after:]
assert re.sub(r'\s','',before[start:end])==re.sub(r'\s','',after[start_after:end_after])
lines=after[start_after:end_after].splitlines();assert len(lines)==9
assert all('"'+code+'"' in lines[i] for i,code in enumerate(codes))
controls={
 'SQLState_literal_mutation':lex(after.replace('"40P01"','"40001"',1))!=b,
 'literal_internal_space_mutation':lex(after.replace('"OTHER"','" OTHER"',1))!=b,
 'arrow_token_split':lex(after.replace('"P0001" ->','"P0001" - >',1))!=b,
 'maximal_munch_positive':lex('class T { int x=a + +b; }')!=lex('class T { int x=a++b; }'),
}
assert all(controls.values())
for dirname,expected in [('schedule_safe_fail_diagnostics_source','9cafda102559d0f2b062fc0bfda816611d331121cee8e5c75a3412c761a0d3c9'),('schedule_safe_fail_diagnostics_line_correction','50444d54dd0d5f117620ca00999366ab0f6ea60a631844849d35b4b31bac3e41')]:
 m=OWN.parent/dirname/'manifest.safe.json';assert hashlib.sha256(m.read_bytes()).hexdigest()==expected
 for row in json.loads(m.read_text())['files']:assert hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()==row['sha256']
p=OWN/'source'/REL;s=p.lstat();assert s.st_nlink==1 and stat.S_IMODE(s.st_mode)==0o600
report={'verdict':'AUTHOR_MECHANICAL_SOURCE_EQUIVALENCE','before_sha256':hashlib.sha256(before.encode()).hexdigest(),
 'after_sha256':hashlib.sha256(after.encode()).hexdigest(),'exact_one_whitespace_hunk':True,
 'full_ordered_tokens_literals_comments_delimiters_equal':True,'counts':{k:len(v) for k,v in b.items()},
 'full_consuming_lexer':True,'text_block_bytes_preserved':True,'controls':controls,
 'all_original9caf_and5044_manifest_members_unchanged':True,'Root_bytes_unchanged':True,
 'formatter_required_layout':'eight SQLState literals separate lines; RHS state separate line',
 'new_formatter_compiler_or_native':'NOT_RUN','source_file_mode':'0o600','nlink':1,
 'requested_model_effort':'gpt-6.1-sol/high','actual_model_effort':'UNKNOWN'}
p=OWN/'equivalence.safe.json';p.write_text(json.dumps(report,indent=2)+'\n');p.chmod(0o600)
print(json.dumps({'counts':report['counts'],'all_equal':True,'negative_controls':len(controls),'runtime':False}))
