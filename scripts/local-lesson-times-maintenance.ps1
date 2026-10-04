[CmdletBinding()]
param(
 [Parameter(Mandatory)][ValidateSet('Inspect','NormalizeTestData','RollbackTestData')][string]$Action,
 [Parameter(Mandatory)][string]$CatalogPath,
 [Parameter(Mandatory)][string]$CatalogSha256,
 [string]$PrivateDirectory = "$env:LOCALAPPDATA/RutCampusTrack/local-stand",
 [string]$InventoryPath, [string]$ApprovedInventorySha256, [string]$LeasePath, [string]$LeaseSha256
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = 'rct-local-persistent'
$sourceRevision = '37ca7cd9a73d14a6a3cb5fd9a1b5927bece15f54'
function Assert-Path([string]$Path) {
 if (-not [IO.Path]::IsPathFullyQualified($Path) -or $Path -match '[\r\n\x00]' -or
     $Path.Replace('\','/') -match '(^|/)\.{1,2}(/|$)' -or $Path -notmatch '^[A-Za-z]:[\\/][^:]+$') { throw 'PATH_REFUSED' }
 $item = [IO.Path]::GetFullPath($Path)
 while ($item) {
  if ((Get-Item -LiteralPath $item -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'PATH_REFUSED' }
  $item = [IO.Path]::GetDirectoryName($item)
 }
}
function Read-Pinned([string]$Path, [string]$Hash) {
 Assert-Path $Path
 if ($Hash -notmatch '^[a-fA-F0-9]{64}$' -or (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ine $Hash) { throw 'PIN_REFUSED' }
 [IO.File]::ReadAllText($Path)
}
function Assert-Keys($Element, [string[]]$Keys) {
 if ($Element.ValueKind -ne [Text.Json.JsonValueKind]::Object) { throw 'CATALOG_SCHEMA_REFUSED' }
 $actual = @($Element.EnumerateObject() | ForEach-Object Name)
 if ($actual.Count -ne $Keys.Count -or @($actual | Where-Object { $_ -cnotin $Keys }).Count -or
     @($actual | Sort-Object -Unique).Count -ne $Keys.Count) { throw 'CATALOG_SCHEMA_REFUSED' }
}
function Read-Catalog([string]$Path, [string]$Hash) {
 $doc = [Text.Json.JsonDocument]::Parse((Read-Pinned $Path $Hash))
 try {
  Assert-Keys $doc.RootElement @('timezone','slots')
  if ($doc.RootElement.GetProperty('timezone').GetString() -cne 'Europe/Moscow' -or
      $doc.RootElement.GetProperty('slots').ValueKind -ne [Text.Json.JsonValueKind]::Array) { throw 'CATALOG_SCHEMA_REFUSED' }
  $slots = @($doc.RootElement.GetProperty('slots').EnumerateArray())
  if ($slots.Count -ne 8) { throw 'CATALOG_SCHEMA_REFUSED' }
  $result = @(); $previousEnd = [TimeSpan]::Zero
  for ($index = 0; $index -lt 8; $index++) {
   $slot = $slots[$index]; Assert-Keys $slot @('lessonNumber','startTime','endTime')
   if ($slot.GetProperty('lessonNumber').ValueKind -ne [Text.Json.JsonValueKind]::Number -or
       $slot.GetProperty('lessonNumber').GetInt32() -ne $index + 1) { throw 'CATALOG_SCHEMA_REFUSED' }
   $times = @()
   foreach ($key in @('startTime','endTime')) {
    $value = $slot.GetProperty($key).GetString()
    if ($value -cnotmatch '^(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?$') { throw 'CATALOG_TIME_REFUSED' }
    if ($value.Length -eq 5) { $value += ':00' }
    $times += [TimeSpan]::ParseExact($value,'hh\:mm\:ss',[Globalization.CultureInfo]::InvariantCulture)
   }
   if ($times[0] -ge $times[1] -or $times[0] -lt $previousEnd) { throw 'CATALOG_TIME_REFUSED' }
   $previousEnd = $times[1]
   $result += [ordered]@{lessonNumber=$index+1;startTime=$times[0].ToString('hh\:mm\:ss');endTime=$times[1].ToString('hh\:mm\:ss')}
  }
  ,$result
 } finally { $doc.Dispose() }
}
function Assert-Private([string]$Path) {
 Assert-Path $Path
 $allowed = [IO.Path]::GetFullPath("$env:LOCALAPPDATA/RutCampusTrack")
 if (-not [IO.Path]::GetFullPath($Path).StartsWith($allowed+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'PRIVATE_SCOPE_REFUSED' }
 $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value; $acl = Get-Acl -LiteralPath $Path
 if (-not $acl.AreAccessRulesProtected -or @($acl.Access | Where-Object {
  $_.AccessControlType -eq 'Allow' -and $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid
 }).Count) { throw 'PRIVATE_ACL_REFUSED' }
}
function New-PrivateRun([string]$Private) {
 $run = Join-Path $Private ('lesson-times-'+[Guid]::NewGuid().ToString('N'))
 $null = New-Item -ItemType Directory -Path $run
 $null = & icacls $run '/inheritance:r' '/grant:r' ("*"+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value+':(OI)(CI)F') 2>&1
 if ($LASTEXITCODE -ne 0) { throw 'PRIVATE_ACL_REFUSED' }; Assert-Private $run
 $run
}
function Invoke-Docker([string[]]$Arguments) {
 $result = @(& docker @Arguments 2>&1)
 if ($LASTEXITCODE -ne 0) { throw 'DOCKER_FAILED_OUTPUT_WITHHELD' }; $result
}
function Get-StandIdentity {
 $ids = @(Invoke-Docker @('ps','-aq','--filter',"label=com.docker.compose.project=$project",'--filter','label=com.docker.compose.service=postgres-schedule'))
 if ($ids.Count -ne 1 -or $ids[0] -cnotmatch '^[a-f0-9]{12,64}$') { throw 'STAND_IDENTITY_REFUSED' }
 # Only nonsecret metadata. Never select Config.Env.
 $template = '{"id":{{json .Id}},"project":{{json (index .Config.Labels "com.docker.compose.project")}},"service":{{json (index .Config.Labels "com.docker.compose.service")}},"owner":{{json (index .Config.Labels "io.rutcampustrack.local-stand")}},"running":{{json .State.Running}},"startedAt":{{json .State.StartedAt}},"mounts":{{json .Mounts}}}'
 $db = (Invoke-Docker @('inspect','--format',$template,$ids[0]) -join [Environment]::NewLine) | ConvertFrom-Json
 $mounts = @($db.mounts | Where-Object Destination -CEQ '/var/lib/postgresql/data')
 if ($db.project -cne $project -or $db.service -cne 'postgres-schedule' -or $db.owner -cne 'persistent' -or
     -not $db.running -or $mounts.Count -ne 1 -or $mounts[0].Type -cne 'volume' -or
     $mounts[0].Name -cne "$project-postgres-schedule-data" -or -not $mounts[0].RW) { throw 'STAND_IDENTITY_REFUSED' }
 $template = '{"name":{{json .Name}},"createdAt":{{json .CreatedAt}},"project":{{json (index .Labels "com.docker.compose.project")}},"role":{{json (index .Labels "com.docker.compose.volume")}},"driver":{{json .Driver}},"scope":{{json .Scope}}}'
 $volume = (Invoke-Docker @('volume','inspect','--format',$template,$mounts[0].Name) -join [Environment]::NewLine) | ConvertFrom-Json
 if ($volume.name -cne "$project-postgres-schedule-data" -or $volume.project -cne $project -or
     $volume.role -cne 'postgres-schedule-data' -or $volume.driver -cne 'local' -or $volume.scope -cne 'local') { throw 'STAND_IDENTITY_REFUSED' }
 [ordered]@{containerId=$db.id;startedAt=$db.startedAt;volume=$volume.name;volumeCreatedAt=$volume.createdAt}
}
function Assert-WritersStopped {
 $ids = @(Invoke-Docker @('ps','-q','--filter',"label=com.docker.compose.project=$project"))
 foreach ($id in $ids) {
  $template = '{"service":{{json (index .Config.Labels "com.docker.compose.service")}},"owner":{{json (index .Config.Labels "io.rutcampustrack.local-stand")}},"helper":{{json (index .Config.Labels "io.rutcampustrack.private-file-loader")}}}'
  $item = (Invoke-Docker @('inspect','--format',$template,$id) -join [Environment]::NewLine) | ConvertFrom-Json
  if ($item.owner -cne 'persistent' -or $item.helper -or $item.service -cnotin
      @('postgres-academic','postgres-schedule','mongo-attendance','redis','rabbitmq')) { throw 'WRITERS_NOT_QUIESCED' }
 }
}
function Assert-Lease($Lease,$Identity,[string]$Hash) {
 $keys = @('schemaVersion','project','containerId','catalogSha256','writersQuiesced','externalPendingClear','expiresAt')
 if (@($Lease.PSObject.Properties).Count -ne $keys.Count -or @($Lease.PSObject.Properties.Name | Where-Object { $_ -cnotin $keys }).Count -or
     $Lease.schemaVersion -ne 1 -or $Lease.project -cne 'rct-local-persistent' -or $Lease.containerId -cne $Identity.containerId -or
     $Lease.catalogSha256 -ine $Hash -or $Lease.writersQuiesced -isnot [bool] -or -not $Lease.writersQuiesced -or
     $Lease.externalPendingClear -isnot [bool] -or -not $Lease.externalPendingClear -or
     [DateTimeOffset]::Parse($Lease.expiresAt).ToUniversalTime() -le [DateTimeOffset]::UtcNow.AddMinutes(2)) { throw 'LEASE_REFUSED' }
}
function Invoke-Sql([string]$ContainerId,[string]$Sql) {
 # No password, TTY, private env file or host endpoint. Native errors withheld.
 $result = @($Sql | & docker exec -i $ContainerId psql -X -q -A -t -v ON_ERROR_STOP=1 -U rct_user -d schedule_db 2>&1)
 if ($LASTEXITCODE -ne 0) { throw 'SQL_FAILED_OUTPUT_WITHHELD' }
 ($result -join [Environment]::NewLine).Trim()
}
function Get-SqlJson([string]$Json) {
 $hex = [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($Json))
 "convert_from(decode('$hex','hex'),'UTF8')::jsonb"
}
function Get-InventoryQuery($Slots,[string[]]$Tables) {
 $catalog = Get-SqlJson (ConvertTo-Json -InputObject @($Slots) -Depth 5 -Compress)
 $digests = foreach ($table in $Tables) {
  if ($table -cnotmatch '^[a-z_][a-z0-9_]*$') { throw 'SCHEMA_REFUSED' }
  $row = if ($table -cin @('lessons','schedule_items')) { "to_jsonb(t)-'start_time'-'end_time'" } else { 'to_jsonb(t)' }
  "SELECT '$table' AS name,count(*) AS rows,encode(sha256(convert_to(coalesce(string_agg(encode(sha256(convert_to(($row)::text,'UTF8')),'hex'),'' ORDER BY encode(sha256(convert_to(($row)::text,'UTF8')),'hex')),''),'UTF8')),'hex') AS digest FROM public.$table t"
 }
 $sql = @'
WITH slots AS (SELECT * FROM jsonb_to_recordset(@CATALOG@) AS s("lessonNumber" int,"startTime" time,"endTime" time)),
changed AS (
 SELECT 'schedule_items' AS table_name,t.id,t.lesson_number,t.start_time::text,t.end_time::text,
 s."startTime"::text AS new_start_time,s."endTime"::text AS new_end_time
 FROM public.schedule_items t JOIN slots s ON s."lessonNumber"=t.lesson_number
 WHERE (t.start_time,t.end_time) IS DISTINCT FROM (s."startTime",s."endTime")
 UNION ALL
 SELECT 'lessons',t.id,t.lesson_number,t.start_time::text,t.end_time::text,s."startTime"::text,s."endTime"::text
 FROM public.lessons t JOIN slots s ON s."lessonNumber"=t.lesson_number
 WHERE (t.start_time,t.end_time) IS DISTINCT FROM (s."startTime",s."endTime")
), digests AS (@DIGESTS@), guards AS (
 SELECT c.relname AS table_name,t.tgname AS name,t.tgenabled::text AS state,
 pg_get_triggerdef(t.oid) AS definition,pg_get_functiondef(t.tgfoid) AS function_definition
 FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND NOT t.tgisinternal
), pending AS (
 SELECT 'lesson_transfer_operations' AS name,count(*) AS count FROM public.lesson_transfer_operations WHERE state <> 'COMPLETED'
 UNION ALL SELECT 'schedule_assignment_replacement_operations',count(*) FROM public.schedule_assignment_replacement_operations WHERE state <> 'COMMITTED'
 UNION ALL SELECT 'schedule_semester_archive_barriers',count(*) FROM public.schedule_semester_archive_barriers WHERE participant_state NOT IN ('RELEASED','DELETED')
 UNION ALL SELECT 'schedule_semester_archive_effect_ledger',count(*) FROM public.schedule_semester_archive_effect_ledger WHERE state <> 'APPLIED'
 UNION ALL SELECT 'lesson_homework_bindings',count(*) FROM public.lesson_homework_bindings WHERE state='PENDING' OR pending_edit_operation_id IS NOT NULL
 UNION ALL SELECT 'homework_placement_operations',count(*) FROM public.homework_placement_operations WHERE state NOT IN ('ACKNOWLEDGED','NOT_ACCEPTED')
)
SELECT jsonb_build_object(
 'identity',jsonb_build_object('database',current_database(),'user',current_user,'systemIdentifier',(SELECT system_identifier::text FROM pg_control_system())),
 'rows',coalesce((SELECT jsonb_agg(to_jsonb(r) ORDER BY table_name,id) FROM changed r),'[]'::jsonb),
 'invalidNumbers',(SELECT count(*) FROM (SELECT lesson_number FROM public.schedule_items UNION ALL SELECT lesson_number FROM public.lessons) n WHERE lesson_number IS NULL OR lesson_number NOT IN (SELECT "lessonNumber" FROM slots)),
 'pending',(SELECT jsonb_agg(to_jsonb(p) ORDER BY name) FROM pending p),
 'digests',(SELECT jsonb_agg(to_jsonb(d) ORDER BY name) FROM digests d),
 'guards',(SELECT jsonb_agg(to_jsonb(g) ORDER BY table_name,name) FROM guards g),
 'tables',(SELECT jsonb_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname='public'))
'@
 $sql.Replace('@CATALOG@',$catalog).Replace('@DIGESTS@',($digests -join ([Environment]::NewLine+'UNION ALL'+[Environment]::NewLine)))
}
function Assert-Inventory($Data) {
 if ($Data.identity.database -cne 'schedule_db' -or $Data.identity.user -cne 'rct_user' -or
     $Data.identity.systemIdentifier -cnotmatch '^[0-9]+$') { throw 'DATABASE_IDENTITY_REFUSED' }
 $pendingNames = @('lesson_transfer_operations','schedule_assignment_replacement_operations','schedule_semester_archive_barriers','schedule_semester_archive_effect_ledger','lesson_homework_bindings','homework_placement_operations')
 if (@($Data.pending).Count -ne $pendingNames.Count -or
     @($Data.pending.name | Sort-Object -Unique).Count -ne $pendingNames.Count -or
     @($Data.pending | Where-Object { $_.name -cnotin $pendingNames -or $_.count -lt 0 }).Count) { throw 'INVENTORY_REFUSED' }
 if ($Data.invalidNumbers -ne 0 -or @($Data.pending | Where-Object count -NE 0).Count) { throw 'DATA_PREREQUISITE_REFUSED' }
 $guard = @($Data.guards | Where-Object { $_.table_name -ceq 'lessons' -and $_.name -ceq 'lessons_history_guard_trg' })
 if ($guard.Count -ne 1 -or $guard[0].state -cnotin @('O','A') -or
     $guard[0].definition -cnotmatch 'validate_lesson_physical_snapshot' -or
     $guard[0].function_definition -cnotmatch '^CREATE OR REPLACE FUNCTION public\.validate_lesson_physical_snapshot\(\)' -or
     $guard[0].function_definition -cnotmatch 'physical lesson identity snapshot is immutable') { throw 'GUARD_REFUSED' }
 foreach ($row in @($Data.rows)) {
  if ($row.table_name -cnotin @('lessons','schedule_items') -or $row.id -le 0 -or $row.lesson_number -notin (1..8)) { throw 'INVENTORY_REFUSED' }
 }
}
function Get-MutationSql($Inventory,$Slots,[switch]$Rollback) {
 $query = Get-InventoryQuery $Slots @($Inventory.data.tables)
 $expected = Get-SqlJson ($Inventory.data | ConvertTo-Json -Depth 30 -Compress)
 $rows = Get-SqlJson (ConvertTo-Json -InputObject @($Inventory.data.rows) -Depth 10 -Compress)
 $locks = @($Inventory.data.tables | ForEach-Object {
  if ($_ -cnotmatch '^[a-z_][a-z0-9_]*$') { throw 'SCHEMA_REFUSED' }; "public.$_"
 }) -join ','
 $guard = @($Inventory.data.guards | Where-Object { $_.table_name -ceq 'lessons' -and $_.name -ceq 'lessons_history_guard_trg' })[0]
 $restore = if ($guard.state -ceq 'A') { 'ENABLE ALWAYS TRIGGER' } else { 'ENABLE TRIGGER' }
 $start = if ($Rollback) { 'start_time' } else { 'new_start_time' }; $end = if ($Rollback) { 'end_time' } else { 'new_end_time' }
 $pre = if ($Rollback) {
  "IF actual-'rows' IS DISTINCT FROM expected-'rows' OR actual->'rows' <> '[]'::jsonb THEN RAISE EXCEPTION 'ROLLBACK_BASELINE_CHANGED'; END IF;"
 } else { "IF actual IS DISTINCT FROM expected THEN RAISE EXCEPTION 'APPROVED_INVENTORY_CHANGED'; END IF;" }
 $post = if ($Rollback) {
  "IF actual IS DISTINCT FROM expected THEN RAISE EXCEPTION 'ROLLBACK_POSTIMAGE_MISMATCH'; END IF;"
 } else {
  "IF actual-'rows' IS DISTINCT FROM expected-'rows' OR actual->'rows' <> '[]'::jsonb THEN RAISE EXCEPTION 'POSTIMAGE_MISMATCH'; END IF;"
 }
 $sql = @'
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SET LOCAL search_path = pg_catalog, public;
SELECT pg_advisory_xact_lock(728413920104);
LOCK TABLE @LOCKS@ IN SHARE ROW EXCLUSIVE MODE;
DO $maintenance$
DECLARE expected jsonb := @EXPECTED@; actual jsonb; affected bigint; wanted bigint;
BEGIN
 SELECT result INTO actual FROM (@QUERY@) AS inventory(result);
 @PRE@
 ALTER TABLE public.lessons DISABLE TRIGGER lessons_history_guard_trg;
 SELECT count(*) INTO wanted FROM jsonb_to_recordset(@ROWS@) AS r(table_name text) WHERE table_name='schedule_items';
 UPDATE public.schedule_items t SET start_time=r.@START@::time,end_time=r.@END@::time
 FROM jsonb_to_recordset(@ROWS@) AS r(table_name text,id bigint,start_time text,end_time text,new_start_time text,new_end_time text)
 WHERE r.table_name='schedule_items' AND t.id=r.id;
 GET DIAGNOSTICS affected = ROW_COUNT;
 IF affected <> wanted THEN RAISE EXCEPTION 'TEMPLATE_ROW_COUNT_CHANGED'; END IF;
 SELECT count(*) INTO wanted FROM jsonb_to_recordset(@ROWS@) AS r(table_name text) WHERE table_name='lessons';
 UPDATE public.lessons t SET start_time=r.@START@::time,end_time=r.@END@::time
 FROM jsonb_to_recordset(@ROWS@) AS r(table_name text,id bigint,start_time text,end_time text,new_start_time text,new_end_time text)
 WHERE r.table_name='lessons' AND t.id=r.id;
 GET DIAGNOSTICS affected = ROW_COUNT;
 IF affected <> wanted THEN RAISE EXCEPTION 'LESSON_ROW_COUNT_CHANGED'; END IF;
 ALTER TABLE public.lessons @RESTORE@ lessons_history_guard_trg;
 SELECT result INTO actual FROM (@QUERY@) AS inventory(result);
 @POST@
END
$maintenance$;
COMMIT;
'@
 $sql.Replace('@LOCKS@',$locks).Replace('@EXPECTED@',$expected).Replace('@QUERY@',$query).Replace('@PRE@',$pre).Replace('@POST@',$post).Replace('@ROWS@',$rows).Replace('@START@',$start).Replace('@END@',$end).Replace('@RESTORE@',$restore)
}
# Fixed safe failure only: never echo native output, inputs, rows or guard body.
try {
 $slots = Read-Catalog $CatalogPath $CatalogSha256
 $private = [IO.Path]::GetFullPath($PrivateDirectory); Assert-Private $private
 $identity = Get-StandIdentity; $lock = $null
 try {
  if ($Action -ine 'Inspect') {
   Assert-Private ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($InventoryPath)))
   $raw = Read-Pinned $InventoryPath $ApprovedInventorySha256; $inventory = $raw | ConvertFrom-Json
   if ($inventory.schemaVersion -ne 1 -or $inventory.project -cne $project -or $inventory.catalogSha256 -ine $CatalogSha256 -or
       ($inventory.container | ConvertTo-Json -Compress) -cne ($identity | ConvertTo-Json -Compress)) { throw 'INVENTORY_REFUSED' }
   Assert-Inventory $inventory.data
   $lease = (Read-Pinned $LeasePath $LeaseSha256) | ConvertFrom-Json
   Assert-Lease $lease $identity $CatalogSha256; Assert-WritersStopped
   $lock = [IO.File]::Open((Join-Path $private 'lesson-times-maintenance.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
   # Preserve the approved exact preimage (times + guards + all table digests).
   $run = New-PrivateRun $private
   [IO.File]::WriteAllText((Join-Path $run 'preimage.json'),$raw,[Text.UTF8Encoding]::new($false))
   [IO.File]::WriteAllText((Join-Path $run 'lease.json'),($lease | ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
   $null = Read-Catalog $CatalogPath $CatalogSha256; $null = Read-Pinned $InventoryPath $ApprovedInventorySha256
   $null = Read-Pinned $LeasePath $LeaseSha256; Assert-Lease $lease $identity $CatalogSha256; Assert-WritersStopped
   $sql = Get-MutationSql $inventory $slots -Rollback:($Action -ieq 'RollbackTestData')
   [IO.File]::WriteAllText((Join-Path $run 'operation.sql'),$sql,[Text.UTF8Encoding]::new($false))
   $null = Invoke-Sql $identity.containerId $sql
   [IO.File]::WriteAllText((Join-Path $run 'committed.txt'),$Action,[Text.UTF8Encoding]::new($false))
   [ordered]@{status='COMMITTED';action=$Action;project=$project;rows=@($inventory.data.rows).Count;approvedInventorySha256=$ApprovedInventorySha256.ToLowerInvariant();backupDirectory=$run} | ConvertTo-Json -Compress
  } else {
   $tableJson = Invoke-Sql $identity.containerId "BEGIN READ ONLY; SELECT coalesce(jsonb_agg(tablename ORDER BY tablename),'[]'::jsonb) FROM pg_tables WHERE schemaname='public'; COMMIT;"
   $tables = @($tableJson | ConvertFrom-Json)
   if ($tables.Count -eq 0) { throw 'SCHEMA_REFUSED' }
   $query = Get-InventoryQuery $slots $tables
   $data = (Invoke-Sql $identity.containerId ('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;'+[Environment]::NewLine+$query+';'+[Environment]::NewLine+'COMMIT;')) | ConvertFrom-Json
   if ((ConvertTo-Json -InputObject $tables -Compress) -cne (ConvertTo-Json -InputObject @($data.tables) -Compress)) { throw 'SCHEMA_REFUSED' }
   $run = New-PrivateRun $private
   $inventory = [ordered]@{schemaVersion=1;project=$project;sourceRevision=$sourceRevision;catalogSha256=$CatalogSha256.ToLowerInvariant();container=$identity;data=$data}
   $path = Join-Path $run 'inventory.json'
   [IO.File]::WriteAllText($path,($inventory | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
   [ordered]@{status='INSPECTED';project=$project;invalidNumbers=$data.invalidNumbers;pending=$data.pending;rows=@($data.rows).Count;templates=@($data.rows | Where-Object table_name -CEQ 'schedule_items').Count;lessons=@($data.rows | Where-Object table_name -CEQ 'lessons').Count;inventoryPath=$path;inventorySha256=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant();catalogSha256=$CatalogSha256.ToLowerInvariant()} | ConvertTo-Json -Compress
   Assert-Inventory $data
  }
 } finally { if ($lock) { $lock.Dispose() } }
} catch {
 [Console]::Error.WriteLine('LESSON_TIMES_MAINTENANCE_REFUSED; details withheld; no successful COMMIT receipt emitted')
 exit 1
}
