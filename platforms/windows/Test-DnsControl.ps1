$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\DnsControl.ps1"
$stillRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('still-dns-test-' + [guid]::NewGuid())
$id = '866137dd-44ca-400d-9033-2cc559aa36c4'
$script:mockAdapter = [pscustomobject]@{ InterfaceGuid = [guid]$id; ifIndex = 7; Name = 'Test Wi-Fi'; Status = 'Up'; HardwareInterface = $true }
$script:current = @{ IPv4 = @('192.0.2.53'); IPv6 = @('2001:db8::53') }
$script:fail6 = $false
$script:alwaysFail = $false
$script:restoreCalls = 0
$script:missing6 = $false
function Get-NetAdapter { param([switch]$IncludeHidden) return $script:mockAdapter }
function Get-DnsClientServerAddress {
    param($InterfaceIndex, $AddressFamily, $ErrorAction)
    if ($script:missing6 -and $AddressFamily -eq 'IPv6') { return }
    [pscustomobject]@{ Family = $AddressFamily; ServerAddresses = $script:current[$AddressFamily] }
}
function Get-ItemPropertyValue {
    param($LiteralPath, $Name, $ErrorAction)
    if ($LiteralPath -like '*Tcpip6*') { return '2001:db8::53' }
    return '' # IPv4 was automatic, and must be restored as automatic.
}
function Set-DnsClientServerAddress {
    param([Parameter(ValueFromPipeline)]$InputObject, [switch]$ResetServerAddresses, $ServerAddresses)
    process {
        if ($script:alwaysFail) { throw 'Simulated persistent failure' }
        if ($script:fail6 -and $InputObject.Family -eq 'IPv6') { $script:fail6 = $false; throw 'Simulated IPv6 failure' }
        if ($ResetServerAddresses) { $script:current[$InputObject.Family] = @('192.0.2.53'); $script:restoreCalls++ }
        else { $script:current[$InputObject.Family] = @($ServerAddresses) }
    }
}
function Clear-DnsClientCache { param($ErrorAction) }
function Assert($condition, $message) { if (-not $condition) { throw "FAIL: $message" } }
function Expect-Failure([scriptblock]$call, $pattern) {
    $failed = $false
    try { & $call | Out-Null } catch { $failed = $_.Exception.Message -like $pattern }
    Assert $failed "Expected failure matching: $pattern"
}

try {
    $AdapterId = $id
    $Action = 'Enable'
    Invoke-StillChange | Out-Null
    $path = Join-Path $stillRoot "$id.json"
    Assert (Test-Path -LiteralPath $path) 'Backup exists before changing DNS'
    $saved = Get-Content -LiteralPath $path -Raw
    $status = Get-StillStatus | ConvertFrom-Json
    Assert ($status.Configured -and $status.CanRestore) 'Both address families configured'
    Expect-Failure { Invoke-StillChange } '*already exists*'
    Assert ((Get-Content -LiteralPath $path -Raw) -eq $saved) 'Repeated enable preserves backup'
    $Action = 'Restore'
    Invoke-StillChange | Out-Null
    Assert (-not (Test-Path -LiteralPath $path)) 'Successful restore removes backup'
    Assert ($script:restoreCalls -eq 1) 'DHCP mode restored'
    Assert ($script:current.IPv6[0] -eq '2001:db8::53') 'Static IPv6 DNS restored'
    Expect-Failure { Invoke-StillChange } '*No saved*'
    $script:fail6 = $true
    $Action = 'Enable'
    Expect-Failure { Invoke-StillChange } '*original settings were restored*'
    Assert ($script:current.IPv4[0] -eq '192.0.2.53' -and $script:current.IPv6[0] -eq '2001:db8::53') 'Partial failure rolls both families back'
    Assert (-not (Test-Path -LiteralPath $path)) 'Rollback removes backup after success'
    $script:alwaysFail = $true
    Expect-Failure { Invoke-StillChange } '*original settings remain saved*'
    Assert (Test-Path -LiteralPath $path) 'Failed rollback retains recovery backup'
    $script:alwaysFail = $false
    $Action = 'Restore'
    Invoke-StillChange | Out-Null
    $Action = 'Enable'
    $script:mockAdapter.Status = 'Disconnected'
    Expect-Failure { Invoke-StillChange } '*Connect this adapter*'
    Assert (-not (Test-Path -LiteralPath $path)) 'Disconnected adapter never modified'
    $script:mockAdapter.Status = 'Up'
    $AdapterId = 'not-a-guid'
    Expect-Failure { Invoke-StillChange } '*Guid*'
    $AdapterId = $id
    $script:missing6 = $true
    Invoke-StillChange | Out-Null
    $status = Get-StillStatus | ConvertFrom-Json
    Assert $status.Configured 'IPv4-only adapter configured without enabling IPv6'
    $Action = 'Restore'
    Invoke-StillChange | Out-Null
    Assert (-not (Test-Path -LiteralPath $path)) 'IPv4-only backup restores successfully'
    Write-Output 'PASS: DNS backup, both families, repeat enable, DHCP/static restore, failure rollback, recovery backup, disconnected adapter, invalid identifier, IPv4-only adapter.'
} finally {
    # Only remove the exact temporary directory created by this test.
    $resolved = [System.IO.Path]::GetFullPath($stillRoot)
    $tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolved.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -like 'still-dns-test-*') {
        Remove-Item -LiteralPath $resolved -Recurse -Force -ErrorAction SilentlyContinue
    }
}
