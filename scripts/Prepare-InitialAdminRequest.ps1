#!/usr/bin/env pwsh
# Standalone: safe to copy into the owner's ignored private directory.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Directory,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (!$IsWindows) { throw 'This bounded helper requires Windows.' }
$ownerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$systemSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
$allowed = @($ownerSid.Value, $systemSid.Value)

function Assert-NoReparse([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        $item = Get-Item -LiteralPath $cursor -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse paths are refused.' }
        $parent = [IO.Directory]::GetParent($cursor)
        $cursor = if ($null -eq $parent) { $null } else { $parent.FullName }
    }
}
function Assert-Private([string]$Path) {
    Assert-NoReparse $Path
    $acl = Get-Acl -LiteralPath $Path
    if ($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $ownerSid.Value) {
        throw 'Private path must belong to the current owner.'
    }
    $rules = $acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])
    if ($rules.Count -eq 0) { throw 'Private permissions are missing.' }
    foreach ($rule in $rules) {
        if ($rule.IdentityReference.Value -notin $allowed -or
            $rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) {
            throw 'Private permissions must allow only the current owner and SYSTEM.'
        }
    }
}
function Write-NewPrivate([string]$Path, [string]$Text) {
    $bytes = [Text.UTF8Encoding]::new($false, $true).GetBytes($Text)
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    # Record ownership immediately: a later ACL failure must also remove this new private file.
    $created.Add($Path)
    try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    # Parent was verified private before CreateNew; the file is never world-readable.
    $acl = [Security.AccessControl.FileSecurity]::new()
    $acl.SetOwner($ownerSid)
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sid in @($ownerSid, $systemSid)) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $sid, [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow))
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
    Assert-Private $Path
}
function Assert-Request($Request) {
    if ((($Request.Keys | Sort-Object) -join ',') -ne 'initialPassword,login,operationId,profile,version' -or
        ($Request.version -isnot [int] -and $Request.version -isnot [long]) -or
        $Request.version -ne 1 -or $Request.login -cne 'rootadmin' -or
        ([guid]::Parse($Request.operationId).ToString() -cne $Request.operationId)) {
        throw 'Initial administrator metadata is invalid.'
    }
    if ((($Request.profile.Keys | Sort-Object) -join ',') -ne 'firstName,lastName,middleName' -or
        $Request.profile.firstName -cne 'Админ' -or $Request.profile.middleName -cne 'Админович' -or
        $Request.profile.lastName -cne 'Администратов') { throw 'Initial administrator profile is invalid.' }
    $password = $Request.initialPassword
    if ($password -isnot [string] -or $password -cnotmatch '^[a-z]+(?:-[a-z]+){5}[!@#%][0-9]{6}$' -or
        $password.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($password) -gt 72) {
        throw 'Initial administrator password policy failed.'
    }
}

$privateRoot = [IO.Path]::GetFullPath($Directory)
if (!(Test-Path -LiteralPath $privateRoot -PathType Container)) { throw 'Existing private directory is required.' }
Assert-Private $privateRoot
$requestPath = Join-Path $privateRoot 'INITIAL_ADMIN_REQUEST'
$readmePath = Join-Path $privateRoot 'INITIAL_ADMIN_README.txt'
if ($CheckOnly) {
    Assert-Private $requestPath
    Assert-Private $readmePath
    $info = Get-Item -LiteralPath $requestPath -Force
    if ($info.Length -le 0 -or $info.Length -gt 8192) { throw 'Invalid request size.' }
    try {
        Assert-Request (Get-Content -LiteralPath $requestPath -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable)
    } catch { throw 'Private request validation failed; contents are not shown.' }
    Write-Output 'PASS: private ACL, schema, fixed account/profile and password policy; credentials were not printed.'
    return
}
foreach ($path in @($requestPath, $readmePath)) {
    if (Test-Path -LiteralPath $path) { throw 'Existing request or README refused; preserve the original request for retries.' }
}
# Six independent dictionary choices plus a random decimal suffix; no chat-supplied credential.
$words = ('acorn amber apple apron arrow atlas badge baker basil beach berry birch blade bloom board boat ' +
    'brick brook brush cabin cable camel candy cargo cedar chalk charm chest cider cliff cloud ' +
    'coast coral crane creek crown daisy delta diver dream drift eagle earth elm ember field finch flame ' +
    'flint flora flute foam forge frame frost fruit gate gem giant glass globe glove grain grape grass ' +
    'green grove gull haven hazel heart hedge heron hill honey horse hotel house ice ivory jade jewel ' +
    'kayak kite kiwi lake lamp larch laser leaf lemon light lilac lime linen lion lotus lunar maple marsh mason ' +
    'melon metal mint model moon moss mouse music noble north novel nut oak ocean olive onion orbit otter ' +
    'panda paper peach pearl pine pilot plain plant plum pond poppy prism pulse quail queen quilt radar ' +
    'raven reed reef ridge river robin rock rose royal ruby sage sail salt sandy satin scale scarf seed shade ' +
    'shell shore silk slate smile snow solar song spark spice spoon sprig star steam steel stone storm sugar ' +
    'sun swift table thorn tiger toast token torch trail tree tulip tune upper vale vapor venus vine ' +
    'viola vista voice water wave wheat wheel wind wing wood wren yacht zebra').Split(' ', [StringSplitOptions]::RemoveEmptyEntries)
if (($words | Sort-Object -Unique).Count -ne $words.Count -or $words.Count -lt 192 -or
    @($words | Where-Object { $_.Length -gt 5 }).Count) { throw 'Invalid bounded dictionary.' }
$selected = for ($i = 0; $i -lt 6; $i++) { $words[[Security.Cryptography.RandomNumberGenerator]::GetInt32($words.Count)] }
$symbols = '!@#%'
$password = ($selected -join '-') + $symbols[[Security.Cryptography.RandomNumberGenerator]::GetInt32($symbols.Length)] +
    ([Security.Cryptography.RandomNumberGenerator]::GetInt32(1000000)).ToString('D6', [Globalization.CultureInfo]::InvariantCulture)
$request = @{
    version = 1; operationId = [guid]::NewGuid().ToString(); login = 'rootadmin'; initialPassword = $password
    profile = @{ firstName = 'Админ'; middleName = 'Админович'; lastName = 'Администратов' }
}
Assert-Request $request
$created = [Collections.Generic.List[string]]::new()
try {
    Write-NewPrivate $requestPath (($request | ConvertTo-Json -Depth 4 -Compress) + [Environment]::NewLine)
    $readme = @"
INITIAL_ADMIN_REQUEST is the immutable one-time first FULL administrator request.
version=1; operationId is a stable unique retry identifier; login and profile identify the owner account.
initialPassword is the generated temporary password. It must stay private.
Keep this exact file and operationId for retries after partial failure. Do not regenerate.
Login: rootadmin. The temporary password is only in INITIAL_ADMIN_REQUEST.
Preparation has not created an account, uploaded a secret or started deployment.
Upload using PowerShell 7, after the owner approves the selected release:
  Get-Content -LiteralPath .\INITIAL_ADMIN_REQUEST -Raw -Encoding utf8 | gh secret set INITIAL_ADMIN_REQUEST --repo ditekmax/rutcampustrack
Check the native exit code. Do not use --body or print the JSON/password.
Copy password locally without terminal output:
  Set-Clipboard -Value ((Get-Content -LiteralPath .\INITIAL_ADMIN_REQUEST -Raw -Encoding utf8 | ConvertFrom-Json).initialPassword)
A future manual Deploy needs bootstrap_initial_admin=true; automatic Deploy never uses this request.
After confirmed login and password change, owner may delete the GitHub secret and clear the clipboard.
No automatic password-change enforcement or automatic secret deletion is claimed.
"@
    Write-NewPrivate $readmePath ($readme + [Environment]::NewLine)
} catch {
    foreach ($path in $created) { Remove-Item -LiteralPath $path -Force }
    throw 'Private request preparation failed; no credential output is available.'
} finally {
    $password = $null; $request = $null; $selected = $null
}
Write-Output 'PASS: private INITIAL_ADMIN_REQUEST and README created; no account, upload or deployment was performed.'
