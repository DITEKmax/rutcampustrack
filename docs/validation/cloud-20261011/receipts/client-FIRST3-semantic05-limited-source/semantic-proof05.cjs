const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const ts=require('/workspace/rutcampustrack/frontends/node_modules/typescript/lib/typescript.js');
const old='/tmp/rct-first3-ci-format-correction-20261012-attempt02',relative='tests/e2e/fixtures/first3-observer.ts';
const before=fs.readFileSync(path.join(old,'before',relative),'utf8'),after=fs.readFileSync(path.join('/workspace/rct-worktrees/client-ci-first3',relative),'utf8');
const ledger=JSON.parse(fs.readFileSync('/tmp/rct-first3-ci-readonly-diff-20261012-attempt04/supervision/diff04.safe.json'));
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const ignored=new Set(['pos','end','fileName','text','singleQuote','startsOnNewLine','modifierFlagsCache','transformFlags','original','id','nodeCount','identifierCount','symbolCount','parseTime','bindTime']);
const printKinds=new Set([ts.SyntaxKind.ArrayLiteralExpression,ts.SyntaxKind.Block,ts.SyntaxKind.ObjectLiteralExpression]);
function scalars(n){const out={};for(const k of Object.keys(n).sort()){if(ignored.has(k)||(k==='multiLine'&&printKinds.has(n.kind)))continue;const v=n[k];if(v===null||['string','number','boolean'].includes(typeof v))out[k]=v;}if(n.kind!==ts.SyntaxKind.SourceFile&&typeof n.text==='string')out.text=n.text;return out;}
function children(n){const out=[];ts.forEachChild(n,c=>{out.push(c);});return out;}
function owner(n){for(let p=n;p;p=p.parent)if(ts.isFunctionDeclaration(p)||ts.isMethodDeclaration(p))return p.name?.getText()??'TOP_LEVEL';return 'TOP_LEVEL';}
function ancestors(n){const out=[];for(let p=n.parent;p&&out.length<6;p=p.parent)out.push(ts.SyntaxKind[p.kind]);return out;}
function parse(s){return ts.createSourceFile('observer.ts',s,ts.ScriptTarget.ES2022,true);}
function semantic(file,rows){const map=new Map(rows.map(r=>[r.path,r])),used=[];function visit(n,p){
 const record=map.get(p);
 if(record&&ts.isParenthesizedExpression(n)){
  if(ts.SyntaxKind[n.expression.kind]!==record.beforeKind||owner(n)!==record.owner||JSON.stringify(ancestors(n))!==JSON.stringify(record.ancestors))throw new Error('Exact recorded grouping context changed');
  const op=ts.isBinaryExpression(n.parent)?ts.SyntaxKind[n.parent.operatorToken.kind]:'NONE';
  if(op!==record.parentOperatorKind)throw new Error('Recorded grouping operator changed');
  used.push(p);return visit(n.expression,p+'/groupChild');
 }
 return {kind:n.kind,scalars:scalars(n),children:children(n).map((c,i)=>visit(c,p+'/'+i))};
 }return {tree:JSON.stringify(visit(file,'ROOT')),used};}
function literals(file){const out=[];function v(n){if(ts.isLiteralExpression(n)||ts.isTemplateHead(n)||ts.isTemplateMiddle(n)||ts.isTemplateTail(n))out.push({kind:n.kind,text:n.text});ts.forEachChild(n,v);}v(file);return JSON.stringify(out);}
function directives(file){const out=[];function v(n){if(ts.isSourceFile(n)||ts.isBlock(n)){const items=[];for(const s of n.statements){if(!ts.isExpressionStatement(s)||!ts.isStringLiteral(s.expression))break;const raw=s.expression.getText(file);items.push(raw.slice(1,-1));}out.push(items);}ts.forEachChild(n,v);}v(file);return JSON.stringify(out);}
function comments(s){const scanner=ts.createScanner(ts.ScriptTarget.ES2022,false,ts.LanguageVariant.Standard,s),out=[];for(let k=scanner.scan();k!==ts.SyntaxKind.EndOfFileToken;k=scanner.scan())if(k===ts.SyntaxKind.SingleLineCommentTrivia||k===ts.SyntaxKind.MultiLineCommentTrivia)out.push(scanner.getTokenText());return JSON.stringify(out);}
function check(code,left,right,expectedCount){const record=ledger.records.find(r=>r.source===code),groups=record?record.rows.filter(r=>r.code==='ADDED_GROUPING'):[];
 const b=parse(left),a=parse(right),sb=semantic(b,groups),sa=semantic(a,groups);
 return {source:code,rawBeforeSha256:sha(left),rawAfterSha256:sha(right),rawBytesEqual:left===right,groupBefore:sb.used.length,groupAfter:sa.used.length,expectedCount,groupContextsExactlyRecorded:sa.used.length===groups.length&&groups.length===expectedCount,orderedRemainingAstEqual:sb.tree===sa.tree,astBeforeSha256:sha(sb.tree),astAfterSha256:sha(sa.tree),orderedLiteralKindTextEqual:literals(b)===literals(a),directiveRawBodiesEqual:directives(b)===directives(a),parseBefore:b.parseDiagnostics.map(d=>d.code),parseAfter:a.parseDiagnostics.map(d=>d.code)};
}
if(ts.version!=='5.9.3'||ledger.acceptedNormalization!=='NONE')throw new Error('Pinned actual diagnosis required');
const expected={TS_OBSERVER:{record:1,arrayItems:1,idEqual:1,hasMarkPermission:1,domFacts:10},JS_COMMONJS:{idEqual:1,hasMarkPermission:1,domFacts:10},JS_ESNEXT:{idEqual:1,hasMarkPermission:1,domFacts:10}};
for(const r of ledger.records){const tally={};for(const d of r.rows){if(d.code==='ADDED_GROUPING')tally[d.owner]=(tally[d.owner]??0)+1;else if(d.code!=='SCALAR_CHANGED'||JSON.stringify(d.keys)!=='["multiLine"]'||!['ArrayLiteralExpression','Block','ObjectLiteralExpression'].includes(d.beforeKind))throw new Error('Unexpected actual difference');}if(JSON.stringify(Object.entries(tally).sort())!==JSON.stringify(Object.entries(expected[r.source]).sort()))throw new Error('Unexpected grouping inventory');}
const rows=[check('TS_OBSERVER',before,after,14)];
for(const [mode,module] of [['JS_COMMONJS',ts.ModuleKind.CommonJS],['JS_ESNEXT',ts.ModuleKind.ESNext]]){const emit=s=>ts.transpileModule(s,{fileName:'observer.ts',compilerOptions:{target:ts.ScriptTarget.ES2022,module,removeComments:true}}).outputText;rows.push(check(mode,emit(before),emit(after),12));}
function rejectsActual(code,mutated){let rejected=false,reason='NONE';try{const r=check('TS_OBSERVER',before,mutated,14);rejected=!r.orderedRemainingAstEqual||!r.orderedLiteralKindTextEqual||!r.directiveRawBodiesEqual;reason=rejected?'SEMANTIC_TREE_OR_LITERAL_OR_DIRECTIVE':'NONE';}catch{rejected=true;reason='RECORDED_SEMANTIC_CONTEXT_CHANGED';}return {code,stimulusChanged:mutated!==after,rejected,reason};}
function rejectsPublic(code,left,right){const b=parse(left),a=parse(right),same=semantic(b,[]).tree===semantic(a,[]).tree&&directives(b)===directives(a);return {code,stimulusChanged:left!==right,rejected:!same,reason:!same?'SEMANTIC_STRUCTURE_FLAGS_OR_DIRECTIVE':'NONE'};}
const controls=[rejectsActual('LITERAL',after.replace("'COMPLETE'","'WRONG_ACTOR'")),rejectsActual('OPERATOR',after.replace('String(value) === String(expected)','String(value) !== String(expected)')),rejectsActual('BRANCH',after.replace('return embedded','return !embedded')),rejectsActual('CALL_STRUCTURE',after.replace('Object.values(embedded).find(Array.isArray)','Object.values(embedded).filter(Array.isArray)')),rejectsPublic('USE_STRICT_GROUPING',"('use strict'); function control(){return this;}","'use strict'; function control(){return this;}"),rejectsPublic('USE_STRICT_ESCAPE',"'use\\x20strict'; function control(){return this;}","'use strict'; function control(){return this;}"),rejectsPublic('PRECEDENCE','function control(x,y,z){return (x+y)*z;}','function control(x,y,z){return x+y*z;}'),rejectsPublic('OPTIONAL_CHAIN_BREAK','function control(value){return value?.part.field;}','function control(value){return (value?.part).field;}')];
const commentEqual=comments(before)===comments(after);
const pass=commentEqual&&rows.every(r=>!r.parseBefore.length&&!r.parseAfter.length&&r.groupBefore===0&&r.groupAfter===r.expectedCount&&r.groupContextsExactlyRecorded&&r.orderedRemainingAstEqual&&r.orderedLiteralKindTextEqual&&r.directiveRawBodiesEqual)&&controls.every(c=>c.stimulusChanged&&c.rejected);
const output=process.env.RCT_SEMANTIC_PROOF_OUTPUT;if(!output||!path.isAbsolute(output))throw new Error('Own safe output required');
fs.writeFileSync(output,JSON.stringify({state:'EXACT_DIAGNOSED_FORMATTING_SEMANTICS_PROOF',pass,rows,commentsEqual:commentEqual,controls,metadataIgnored:'ONLY multiLine on ArrayLiteralExpression/Block/ObjectLiteralExpression',groupingScope:'ONLY exact path/owner/ancestor/operator/underlyingkind ledger04 14TS and12JS; all other parentheses retained; flags/operators/names/literal/directive structures compared',rawStrict02and03:'FAIL_RETAINED_QUALIFIED',sourceWrites:false},null,2)+'\n');process.exit(pass?0:1);
