param([ValidateSet('Prepare','Validate')][string]$Mode,[Parameter(Mandatory=$true)][string]$RunnerHash)
$ErrorActionPreference='Stop'
$evidence=$PSScriptRoot
$runner=Join-Path $evidence '../../worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1'
if((Get-FileHash $runner).Hash -ne $RunnerHash){throw 'H80 runner freeze mismatch'}
$tokens=$null; $errors=$null
$ast=[System.Management.Automation.Language.Parser]::ParseFile($runner,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Runner parser error'}
foreach($name in @('Assert-That','Assert-JsonString','Assert-JsonInteger','Assert-Sha256Value','Get-MongoSnapshot','Get-ExactObjectProperty','Convert-AttachmentTimestamp','Convert-AttachmentDescriptorToCanonicalObject','Convert-AttachmentDescriptorsToCanonicalText','Assert-I1MongoDelta')){
    $definition=$ast.FindAll({param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq $name},$true)|Select-Object -First 1
    if(!$definition){throw "Missing actual function $name"}
    Invoke-Expression $definition.Extent.Text
}
$rid='abcdef0123456789abcdef01'
if($Mode -eq 'Prepare'){
    if(Test-Path (Join-Path $evidence 'h80-native.js')){throw 'H80 refuses overwrite'}
    $script:infra=@{mongo=@{Id='native-shell-no-db'}}
    function Read-MongoJson {param($ContainerId,$JavaScript,$Purpose) $script:captured=$JavaScript; return '{}'}
    $null=Get-MongoSnapshot -StudentId 42 -RequestId $rid
    $queryJson=ConvertTo-Json -InputObject $script:captured -Compress
    $js=@'
const query = QUERY_JSON;
const rid='abcdef0123456789abcdef01';
const descriptors=[
 {id:'abcdef0123456789abcdef02',name:'fixture.pdf',content_type:'application/pdf',size:10485760,sha256:'a'.repeat(64),state:'ACTIVE',uploaded_at:'2026-09-20T00:00:00.000Z',expires_at:'2026-09-21T00:00:00.000Z',expired_at:null},
 {id:'abcdef0123456789abcdef03',name:'fixture.png',content_type:'image/png',size:10485760,sha256:'b'.repeat(64),state:'ACTIVE',uploaded_at:'2026-09-20T00:00:01.000Z',expires_at:'2026-09-21T00:00:01.000Z',expired_at:null}
];
const cases=[
 ['nativeLong',()=>NumberLong('10485760'),true],
 ['number',()=>10485760,true],
 ['unsafeLong',()=>NumberLong('9007199254740993'),false],
 ['negativeLong',()=>NumberLong('-1'),false],
 ['fraction',()=>1.5,false],
 ['numericString',()=>'10485760',false],
 ['missing',()=>undefined,false],
 ['null',()=>null,false]
];
const results=[];
for(const [name,value,expectedAccept] of cases){
 const attachments=descriptors.map((x,i)=>({_id:new ObjectId(x.id),name:x.name,type:x.content_type,size:value(),sha256:x.sha256,state:x.state,uploaded_at:new Date(x.uploaded_at),expires_at:new Date(x.expires_at),expired_at:null,request_id:rid,position:i}));
 const outbox=[{_id:new ObjectId('abcdef0123456789abcdef04'),event_type:'excuse.requested',status:'NEW',payload:JSON.stringify({payload:{ticket_id:rid,attachments:descriptors}})}];
 const collections={
  excuse_tickets:{countDocuments(){return 1},find(){const c={sort(){return c},toArray(){return [{_id:new ObjectId(rid),status:'submitted'}]}};return c}},
  student_request_receipts:{countDocuments(){return 1}},
  request_attachments:{countDocuments(){return 2},find(){const c={sort(){return c},toArray(){return attachments}};return c}},
  attendance_outbox:{countDocuments(){return 1},find(){return {toArray(){return outbox}}}}
 };
 const db={getSiblingDB(name){if(name!=='attendance_db')throw Error('Unexpected database');return collections}};
 let snapshot=null;
 try {new Function('db','print',query)(db,text=>{snapshot=JSON.parse(text)});results.push({name,expectedAccept,snapshot,error:null})}
 catch(e){results.push({name,expectedAccept,snapshot:null,error:String(e.message)})}
}
print(JSON.stringify({schema:'rct.h80.native-bson.v1',results,descriptors}));
'@
    [IO.File]::WriteAllText((Join-Path $evidence 'h80-native.js'),$js.Replace('QUERY_JSON',$queryJson),[Text.UTF8Encoding]::new($false))
    @{runnerHash=$RunnerHash;nativeScriptHash=(Get-FileHash (Join-Path $evidence 'h80-native.js')).Hash}|ConvertTo-Json|Set-Content (Join-Path $evidence 'h80-freeze.json')
} else {
    if(Test-Path (Join-Path $evidence 'h80-assertions.json')){throw 'H80 refuses overwrite'}
    $native=Get-Content (Join-Path $evidence 'h80-native-output.json') -Raw|ConvertFrom-Json -Depth 30
    $files=@($native.descriptors|ForEach-Object {[pscustomobject]@{id=$_.id;name=$_.name;contentType=$_.content_type;sizeBytes=$_.size;sha256=$_.sha256;state=$_.state;uploadedAt=$_.uploaded_at;expiresAt=$_.expires_at;expiredAt=$null}})
    $probe=[pscustomobject]@{i1=[pscustomobject]@{requestId=$rid;files=$files}}
    $before=[pscustomobject]@{excuseTickets=0;receipts=0;attachments=0;outboxRequested=0}
    $outcomes=@(foreach($case in $native.results){
        $accepted=$false; $errorText=$case.error
        if($null -ne $case.snapshot){try{Assert-I1MongoDelta -Before $before -After $case.snapshot -Probe $probe; $accepted=$true}catch{$errorText=$_.Exception.Message}}
        [pscustomobject]@{case=$case.name;expectedAccept=$case.expectedAccept;accepted=$accepted;pass=($accepted -eq $case.expectedAccept);error=$errorText}
    })
    $outcomes|ConvertTo-Json -Depth 5|Set-Content (Join-Path $evidence 'h80-assertions.json')
    $outcomes|ConvertTo-Json -Depth 5
    if($outcomes.Count -ne 8 -or @($outcomes|Where-Object {-not $_.pass}).Count){throw 'H80 native boundary mismatch'}
}
