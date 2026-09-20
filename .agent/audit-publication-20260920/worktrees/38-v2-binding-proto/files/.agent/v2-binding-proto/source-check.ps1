$ErrorActionPreference = 'Stop'

$baseRevision = 'b8220ac92125a8afa37598b270aa4fab7aa1f470'
$bindingNames = @(
    'HomeworkBindingState',
    'ReserveHomeworkBindingRequest',
    'ConfirmHomeworkBindingRequest',
    'HomeworkBindingResponse',
    'HomeworkBindingsRequest',
    'HomeworkBindingsResponse'
)
$bindingRpcNames = @(
    'ReserveHomeworkBinding',
    'ConfirmHomeworkBinding',
    'GetHomeworkBindings'
)

function Normalize([string] $value) {
    return (($value -replace "`r`n", "`n") -replace "`r", "`n")
}

function Assert-Equal([string] $label, [string] $expected, [string] $actual) {
    if ((Normalize $expected) -ne (Normalize $actual)) {
        throw "$label differs"
    }
    Write-Output "PASS $label"
}

function Assert-Count([string] $label, [string] $value, [string] $pattern, [int] $expected) {
    $count = ([regex]::Matches((Normalize $value), $pattern, [Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($count -ne $expected) {
        throw "$label count $count, expected $expected"
    }
    Write-Output "PASS $label count=$count"
}

function Get-ProtoBlock([string] $text, [string] $kind, [string] $name) {
    $pattern = "(?ms)^$kind\s+$name\s*\{.*?^\}\s*(?:`n|$)"
    $match = [regex]::Match((Normalize $text), $pattern)
    if (-not $match.Success) {
        throw "Missing $kind $name"
    }
    return $match.Value.Trim()
}

function Remove-ProtoBlock([string] $text, [string] $kind, [string] $name) {
    $pattern = "(?ms)^$kind\s+$name\s*\{.*?^\}\s*(?:`n|$)"
    return [regex]::Replace((Normalize $text), $pattern, '')
}

function Remove-BindingSurface([string] $text, [bool] $removeImport) {
    $result = Normalize $text
    if ($removeImport) {
        $result = [regex]::Replace($result, '(?m)^import "schedule\.proto";[ \t]*\r?\n', '')
    }
    foreach ($name in $bindingNames) {
        $result = Remove-ProtoBlock $result 'enum' $name
        $result = Remove-ProtoBlock $result 'message' $name
    }
    foreach ($name in $bindingRpcNames) {
        $result = [regex]::Replace($result, "(?m)^\s*rpc\s+$name\s+.*?;\s*`n", '')
    }
    $result = [regex]::Replace($result, '(?m)^[ \t]*//[ \t]*(?:Зарезервировать|Подтвердить|Прочитать)[^\r\n]*привяз[^\r\n]*\r?\n', '')
    return (($result -split "`n" | Where-Object { $_.Trim().Length -gt 0 }) -join "`n")
}

$academicBaseline = (& git show "${baseRevision}:proto/academic.proto") -join "`n"
$scheduleBaseline = (& git show "${baseRevision}:proto/schedule.proto") -join "`n"
$academicCurrent = [IO.File]::ReadAllText((Join-Path (Get-Location) 'proto\academic.proto'))
$scheduleCurrent = [IO.File]::ReadAllText((Join-Path (Get-Location) 'proto\schedule.proto'))

if ((& git rev-parse HEAD) -ne $baseRevision) {
    throw "Unexpected HEAD"
}

Assert-Equal 'academic unrelated source' (Remove-BindingSurface $academicBaseline $true) (Remove-BindingSurface $academicCurrent $true)
Assert-Equal 'schedule unrelated source' (Remove-BindingSurface $scheduleBaseline $false) (Remove-BindingSurface $scheduleCurrent $false)
Assert-Count 'academic schedule import' $academicCurrent '^import "schedule\.proto";' 0
Assert-Count 'academic binding RPCs' $academicCurrent 'rpc\s+(ReserveHomeworkBinding|ConfirmHomeworkBinding|GetHomeworkBindings)\s*\(' 0

foreach ($name in $bindingRpcNames) {
    Assert-Count "schedule $name RPC" $scheduleCurrent "rpc\s+$name\s+\(" 1
}

foreach ($name in $bindingNames) {
    Assert-Equal "binding $name" (Get-ProtoBlock $academicBaseline ($(if ($name -eq 'HomeworkBindingState') { 'enum' } else { 'message' })) $name) (Get-ProtoBlock $scheduleCurrent ($(if ($name -eq 'HomeworkBindingState') { 'enum' } else { 'message' })) $name)
}

Assert-Count 'schedule current_lesson qualified type' $scheduleCurrent 'rutcampustrack\.schedule\.LessonInfo\s+current_lesson\s*=\s*3;' 1
Assert-Count 'schedule bindings field tag' $scheduleCurrent 'repeated\s+HomeworkBindingResponse\s+bindings\s*=\s*1;' 1
Assert-Count 'schedule occurrence_ids field tag' $scheduleCurrent 'repeated\s+int64\s+occurrence_ids\s*=\s*1;' 1
Assert-Equal 'schedule package' ([regex]::Match((Normalize $scheduleBaseline), '(?m)^package\s+[^;]+;').Value) ([regex]::Match((Normalize $scheduleCurrent), '(?m)^package\s+[^;]+;').Value)
Assert-Equal 'schedule java package' ([regex]::Match((Normalize $scheduleBaseline), '(?m)^option\s+java_package\s*=\s*"[^"]+";').Value) ([regex]::Match((Normalize $scheduleCurrent), '(?m)^option\s+java_package\s*=\s*"[^"]+";').Value)
Write-Output 'SOURCE_CHECK PASS'
