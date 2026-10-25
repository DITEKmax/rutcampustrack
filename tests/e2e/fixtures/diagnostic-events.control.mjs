// Node-only producer/reporter integration: no repository config, globalSetup, browser or API.
import assert from 'node:assert/strict'
import { mkdtempSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { randomBytes } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
const flags = process.argv.slice(2)
assert.equal(
  flags.length <= 1 && flags.every((flag) => flag === '--with-consumer'),
  true,
  'Unsupported control option',
)
const withConsumer = flags.includes('--with-consumer')
const here = dirname(fileURLToPath(import.meta.url))
const out = mkdtempSync(join(tmpdir(), 'rct-diagnostic-controls-'))
const e2e = resolve(here, '..')
const canary = randomBytes(18).toString('hex')
const source = String.raw`
import assert from 'node:assert/strict';
import {test as base} from ${JSON.stringify(join(e2e, 'node_modules/@playwright/test'))};
import {test} from ${JSON.stringify(join(here, 'artifact-privacy.ts'))};
import {datasetHttpFailure} from ${JSON.stringify(join(here, 'safe-reporter.ts'))};
import {diagnosticCase, DiagnosticRun, captureDiagnosticFailure} from ${JSON.stringify(join(here, 'diagnostic-events.ts'))};
const setup=test.extend<{}, {syntheticSetup:void}>({syntheticSetup:[async({},use)=>{throw datasetHttpFailure('POST','/api/academic/homeworks',503);},{scope:'worker'}]});
test('native body', {annotation:diagnosticCase('LIMITED_ADMIN_ACCOUNTS')}, async()=>{throw datasetHttpFailure('POST','/api/academic/homeworks',503);});
setup('native fixture', {annotation:diagnosticCase('HOMEWORK_FOREIGN_RETRY')}, async({syntheticSetup})=>{});
test('plain known text', {annotation:diagnosticCase('SESSION_REVOKE_RECOVERY')}, async()=>{throw new Error('Reported synthetic dataset DATASET_HOMEWORK_CREATE_HTTP: HTTP 503');});
test('different throwable', {annotation:diagnosticCase('ASYNC_DOCX_RESUME')}, async()=>{const caught=datasetHttpFailure('POST','/api/academic/homeworks',503); throw new Error(caught.message);});
test('exact message and stack clone is a different raw Error', {annotation:diagnosticCase('N2_STUDENT_RETRY_SUSPENDED')}, async()=>{const original=datasetHttpFailure('POST','/api/academic/homeworks',503);const foreign=new Error(original.message);foreign.stack=original.stack;throw foreign;});
test('valid proof replay on foreign raw Error', {annotation:diagnosticCase('N2_STUDENT_RETRY_ACTIVE')}, async()=>{
 const original=datasetHttpFailure('POST','/api/academic/homeworks',503); const info:any=test.info();
 info._failWithError(original); const serialized=info.errors.pop();
 captureDiagnosticFailure(test.info(),serialized,()=>{serialized.message='safe';serialized.stack='Error: safe';});
 assert.equal(serialized.__rctDiagnosticProof[0].event.stageId,'DATASET_HTTP');
 const foreign:any=new Error(original.message);foreign.__rctDiagnosticProof=serialized.__rctDiagnosticProof;throw foreign;
});
base('finite private verifier controls', async()=>{
 const previous=process.env.RCT_PRIVATE_DIAGNOSTIC_RUN_KEY;
 const make=()=>{
  const item:any={id:'framework-id',annotations:[diagnosticCase('LIMITED_ADMIN_ACCOUNTS')],parent:{project:()=>({name:'synthetic'})}};
  const run=new DiagnosticRun();run.begin({allTests:()=>[item]} as any);
  const info:any={testId:item.id,annotations:[diagnosticCase('LIMITED_ADMIN_ACCOUNTS')],project:{name:'synthetic'},retry:0,errors:[],_failWithError(error:any){this.errors.push({message:error.message,stack:error.stack});}};
  const error:any={message:'private '+${JSON.stringify(canary)},stack:'Error: private '+${JSON.stringify(canary)}};
  captureDiagnosticFailure(info,error,()=>{error.message='safe';error.stack='Error: safe';});
  return {item,run,error,info,result:{errors:[error],retry:0} as any};
 };
 try {
  let v=make();assert.equal(v.run.read(v.item,v.result)[0].stageId,'FRAMEWORK');assert.equal(v.run.invalid,false);v.run.close();
  v=make();captureDiagnosticFailure(v.info,v.error,()=>{for(const key of Object.keys(v.error))delete v.error[key];v.error.message='safe';v.error.stack='Error: safe';});assert.equal(v.run.read(v.item,v.result).length,1);assert.equal(v.run.invalid,false);v.run.close();
  v=make();v.error.message='late private '+${JSON.stringify(canary)};captureDiagnosticFailure(v.info,v.error,()=>{for(const key of Object.keys(v.error))delete v.error[key];v.error.message='safe';v.error.stack='Error: safe';});v.run.read(v.item,v.result);assert.equal(v.run.invalid,true);v.run.close();
  const mutations=[
   (v:any)=>v.error.__rctDiagnosticProof[0].event.caseId='HOMEWORK_FOREIGN_RETRY',
   (v:any)=>v.error.__rctDiagnosticProof[0].event.stageId='DATASET_HTTP',
   (v:any)=>v.error.__rctDiagnosticProof[0].event.schema='unknown',
   (v:any)=>v.error.__rctDiagnosticProof[0].event.httpStatus=true,
   (v:any)=>v.error.__rctDiagnosticProof[0].event.privateBody=${JSON.stringify(canary)},
   (v:any)=>Object.defineProperty(v.error.__rctDiagnosticProof[0].event,'stageId',{get:()=>{throw new Error('accessor must not run')},enumerable:true}),
   (v:any)=>v.error.__rctDiagnosticProof[0].binding.testId='foreign',
   (v:any)=>v.error.__rctDiagnosticProof[0].binding.project='foreign',
   (v:any)=>v.error.__rctDiagnosticProof[0].binding.project='x'.repeat(129),
   (v:any)=>v.error.__rctDiagnosticProof[0].binding.testId={toJSON:()=>{throw new Error('nested data must not run')}},
   (v:any)=>v.error.__rctDiagnosticProof[0].binding.retry=1,
   (v:any)=>v.error.message='altered',
   (v:any)=>v.error.__rctDiagnosticProof.push(v.error.__rctDiagnosticProof[0]),
   (v:any)=>delete v.error.__rctDiagnosticProof,
   (v:any)=>{v.error.__rctDiagnosticProof[0].event=Object.assign(Object.create({privateBody:${JSON.stringify(canary)}}),v.error.__rctDiagnosticProof[0].event)},
  ];
  for(const mutate of mutations){v=make();mutate(v);v.run.read(v.item,v.result);assert.equal(v.run.invalid,true);v.run.close();}
  v=make();const other=new DiagnosticRun();other.begin({allTests:()=>[v.item]} as any);other.read(v.item,v.result);assert.equal(other.invalid,true);other.close();v.run.close();
  v=make();const foreign={...v.item,id:'other',annotations:[]};v.run.read(foreign as any,v.result);assert.equal(v.run.invalid,true);v.run.close();
  const item:any={id:'one',annotations:[diagnosticCase('LIMITED_ADMIN_ACCOUNTS')],parent:{project:()=>({name:'synthetic'})}};
  let run=new DiagnosticRun();run.begin({allTests:()=>[item,{...item,id:'two'}]} as any);assert.equal(run.invalid,true);run.close();
  run=new DiagnosticRun();run.begin({allTests:()=>[{...item,annotations:[{type:'rct-diagnostic-case.v1',description:'FOREIGN'}]}]} as any);assert.equal(run.invalid,true);run.close();
  // Metadata shifts cannot change the trusted case registration or verifier outcome.
  v=make();v.item.location={file:'/other/checkout/moved.spec.ts',line:999};v.item.title='renamed';v.item.annotations=[];assert.equal(v.run.read(v.item,v.result)[0].caseId,'LIMITED_ADMIN_ACCOUNTS');assert.equal(v.run.invalid,false);v.run.close();
 } finally {if(previous===undefined)delete process.env.RCT_PRIVATE_DIAGNOSTIC_RUN_KEY;else process.env.RCT_PRIVATE_DIAGNOSTIC_RUN_KEY=previous;}
});
`
writeFileSync(join(out, 'controls.spec.ts'), source)
writeFileSync(
  join(out, 'playwright.config.cjs'),
  `module.exports={testDir:${JSON.stringify(out)},testMatch:'controls.spec.ts',outputDir:${JSON.stringify(join(out, 'artifacts'))},workers:1,retries:0,reporter:[[${JSON.stringify(join(here, 'safe-reporter.ts'))}]],use:{trace:'off',video:'off',screenshot:'off'}};`,
)
const child = spawnSync(
  process.execPath,
  [
    join(e2e, 'node_modules/playwright/cli.js'),
    'test',
    '--config',
    join(out, 'playwright.config.cjs'),
  ],
  {
    cwd: e2e,
    encoding: 'utf8',
    env: { ...process.env, PLAYWRIGHT_NO_COPY_PROMPT: '1' },
    timeout: 45000,
    maxBuffer: 1024 * 1024,
  },
)
writeFileSync(join(out, 'child-output.txt'), child.stdout + child.stderr, { mode: 0o600 })
assert.equal(child.error, undefined)
assert.equal(child.status, 1, 'deliberate failing behavior must remain failed')
const text = readFileSync(join(out, 'artifacts/safe-results.json'), 'utf8')
const report = JSON.parse(text)
assert.equal(report.status, 'failed')
assert.equal(report.unsafeArtifact, false)
assert.equal(report.runnerErrors, 0)
assert.equal(report.results.length, 7)
for (const id of ['LIMITED_ADMIN_ACCOUNTS', 'HOMEWORK_FOREIGN_RETRY']) {
  const row = report.results.find((r) => r.caseId === id)
  assert.equal(row.status, 'failed')
  assert.equal(
    row.diagnosticEvents.some(
      (e) =>
        e.stageId === 'DATASET_HTTP' &&
        e.eventCode === 'DATASET_HOMEWORK_CREATE_HTTP' &&
        e.httpStatus === 503,
    ),
    true,
  )
}
for (const id of [
  'SESSION_REVOKE_RECOVERY',
  'ASYNC_DOCX_RESUME',
  'N2_STUDENT_RETRY_ACTIVE',
  'N2_STUDENT_RETRY_SUSPENDED',
]) {
  const row = report.results.find((r) => r.caseId === id)
  assert.equal(row.status, 'failed')
  assert.deepEqual(
    row.diagnosticEvents.map((e) => e.stageId),
    ['FRAMEWORK'],
  )
}
assert.equal(report.results.filter((r) => r.status === 'passed').length, 1)
const artifacts = (directory) =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory()
      ? artifacts(join(directory, entry.name))
      : [readFileSync(join(directory, entry.name), 'utf8')],
  )
const publicArtifacts = artifacts(join(out, 'artifacts')).join('\n')
for (const forbidden of [
  canary,
  '__rctDiagnosticProof',
  'rct-diagnostic-proof.v1',
  'signature',
  'RCT_PRIVATE_DIAGNOSTIC_RUN_KEY',
])
  assert.equal((publicArtifacts + child.stdout + child.stderr).includes(forbidden), false)
if (withConsumer) {
  const consumer = spawnSync(
    'python3',
    [
      '-B',
      join(here, 'http500-diagnostic-contract.py'),
      '--producer-report',
      join(out, 'artifacts/safe-results.json'),
    ],
    { cwd: resolve(e2e, '../..'), encoding: 'utf8', timeout: 45000, maxBuffer: 1024 * 1024 },
  )
  const privateOutput = (consumer.stdout ?? '') + (consumer.stderr ?? '')
  writeFileSync(join(out, 'consumer-output.txt'), privateOutput, { mode: 0o600 })
  // Nonzero/timeout stays a failure; never include the captured Python output in assertions.
  assert.equal(consumer.error === undefined, true, 'Consumer subprocess did not complete')
  assert.equal(consumer.status, 0, 'Consumer contract rejected the actual producer report')
  for (const forbidden of [
    canary,
    '__rctDiagnosticProof',
    'rct-diagnostic-proof.v1',
    'RCT_PRIVATE_DIAGNOSTIC_RUN_KEY',
  ])
    assert.equal(
      privateOutput.includes(forbidden),
      false,
      'Consumer output contained private control data',
    )
}
console.log(
  JSON.stringify({
    schema: 'rct-diagnostic-controls.v1',
    passed: true,
    deliberateChildExit: child.status,
    rows: 7,
    finiteNegatives: 20,
    ...(withConsumer ? { consumerPassed: true } : {}),
    report: join(out, 'artifacts/safe-results.json'),
  }),
)
