param(
    [ValidateSet('Status', 'Enable', 'Restore')][string]$Action = 'Status',
    [string]$AdapterId = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$stillRoot = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Still\DnsBackup'
$servers4 = @('94.140.14.14', '94.140.15.15')
$servers6 = @('2a10:50c0::ad1:ff', '2a10:50c0::ad2:ff')

function Get-StillAdapter([string]$id) {
    $guid = [guid]::Parse($id)
    $found = @(Get-NetAdapter -IncludeHidden | Where-Object { $_.InterfaceGuid -eq $guid })
    if ($found.Count -ne 1) { throw 'The network adapter is unavailable. Reconnect it and refresh.' }
    return $found[0]
}

function Get-FamilyState($adapter, [int]$family) {
    $protocol = if ($family -eq 2) { 'Tcpip' } else { 'Tcpip6' }
    $guid = '{' + ([guid]$adapter.InterfaceGuid).ToString() + '}'
    $key = "HKLM:\SYSTEM\CurrentControlSet\Services\$protocol\Parameters\Interfaces\$guid"
    $static = Get-ItemPropertyValue -LiteralPath $key -Name NameServer -ErrorAction SilentlyContinue
    $addresses = @()
    if ($static) { $addresses = @(([string]$static -split '[,;\s]+') | Where-Object { $_ }) }
    foreach ($address in $addresses) { [void][System.Net.IPAddress]::Parse($address) }
    return @{ Family = $family; Automatic = ($addresses.Count -eq 0); Servers = $addresses }
}

function Set-FamilyState($adapter, $state) {
    $client = Get-DnsClientServerAddress -InterfaceIndex $adapter.ifIndex -AddressFamily $(if ($state.Family -eq 2) { 'IPv4' } else { 'IPv6' })
    if ($state.Automatic) { $client | Set-DnsClientServerAddress -ResetServerAddresses }
    else {
        $validated = @($state.Servers | ForEach-Object { ([System.Net.IPAddress]::Parse($_)).ToString() })
        if ($validated.Count -eq 0) { throw 'The saved static DNS configuration is empty.' }
        $client | Set-DnsClientServerAddress -ServerAddresses $validated
    }
}

function Get-StillStatus {
    $rows = @(foreach ($adapter in @(Get-NetAdapter | Where-Object { $_.HardwareInterface })) {
        $id = ([guid]$adapter.InterfaceGuid).ToString()
        $client4 = @(Get-DnsClientServerAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue)
        $client6 = @(Get-DnsClientServerAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv6 -ErrorAction SilentlyContinue)
        $current4 = @($client4 | ForEach-Object { $_.ServerAddresses })
        $current6 = @($client6 | ForEach-Object { $_.ServerAddresses })
        $supported = ($client4.Count + $client6.Count) -gt 0
        $configured = $supported -and (($client4.Count -eq 0) -or (($current4 -join ',') -eq ($servers4 -join ','))) -and (($client6.Count -eq 0) -or (($current6 -join ',') -eq ($servers6 -join ',')))
        [pscustomobject]@{ Id = $id; Name = $adapter.Name; Link = [string]$adapter.Status;
            Configured = $configured; Supported = $supported; CanRestore = (Test-Path -LiteralPath (Join-Path $stillRoot "$id.json"));
            Dns = (@($current4) + @($current6)) -join ', ' }
    })
    ConvertTo-Json -InputObject $rows -Compress -Depth 5
}

function Invoke-StillChange {
    if ($Action -eq 'Status') { Get-StillStatus; return }
    $adapter = Get-StillAdapter $AdapterId
    if (-not $adapter.HardwareInterface) { throw 'Select a physical Wi-Fi or Ethernet adapter.' }
    $id = ([guid]$adapter.InterfaceGuid).ToString()
    $backupPath = Join-Path $stillRoot "$id.json"
    if ($Action -eq 'Enable') {
        if ($adapter.Status -ne 'Up') { throw 'Connect this adapter before enabling DNS filtering.' }
        if (Test-Path -LiteralPath $backupPath) { throw 'A saved configuration already exists. Restore it before enabling again.' }
        $states = @()
        if (@(Get-DnsClientServerAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue).Count) { $states += Get-FamilyState $adapter 2 }
        if (@(Get-DnsClientServerAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv6 -ErrorAction SilentlyContinue).Count) { $states += Get-FamilyState $adapter 23 }
        if ($states.Count -eq 0) { throw 'This adapter has no available DNS configuration.' }
        $snapshot = @{ Version = 1; AdapterId = $id; States = $states }
        New-Item -ItemType Directory -Path $stillRoot -Force | Out-Null
        # CreateNew prevents a second app instance from replacing the original backup.
        $stream = [System.IO.File]::Open($backupPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes(($snapshot | ConvertTo-Json -Depth 6))
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        } finally { $stream.Dispose() }
        try {
            foreach ($state in $snapshot.States) {
                Set-FamilyState $adapter @{ Family = $state.Family; Automatic = $false; Servers = $(if ($state.Family -eq 2) { $servers4 } else { $servers6 }) }
            }
        } catch {
            $cause = $_.Exception.Message
            try {
                foreach ($state in $snapshot.States) { Set-FamilyState $adapter $state }
                Remove-Item -LiteralPath $backupPath
            } catch { throw "DNS setup failed: $cause. Automatic restoration also failed. The original settings remain saved; use Restore previous DNS. $($_.Exception.Message)" }
            throw "DNS setup failed and the original settings were restored: $cause"
        }
    } else {
        if (-not (Test-Path -LiteralPath $backupPath)) { throw 'No saved DNS configuration exists for this adapter.' }
        $snapshot = Get-Content -LiteralPath $backupPath -Raw | ConvertFrom-Json
        if ($snapshot.Version -ne 1 -or $snapshot.AdapterId -ne $id -or $snapshot.States.Count -lt 1 -or $snapshot.States.Count -gt 2) { throw 'Invalid DNS backup. No settings were changed.' }
        $families = @($snapshot.States | ForEach-Object { $_.Family })
        if (($families -join ',') -notin @('2', '23', '2,23')) { throw 'Invalid DNS address families in backup.' }
        # Validate the whole backup before making the first change.
        foreach ($state in $snapshot.States) {
            if ($state.Automatic -isnot [bool]) { throw 'Invalid DNS mode in backup.' }
            if (-not $state.Automatic -and @($state.Servers).Count -eq 0) { throw 'Empty saved DNS configuration.' }
            foreach ($address in $state.Servers) { [void][System.Net.IPAddress]::Parse($address) }
        }
        foreach ($state in $snapshot.States) { Set-FamilyState $adapter $state }
        Remove-Item -LiteralPath $backupPath
    }
    Clear-DnsClientCache -ErrorAction SilentlyContinue
    Get-StillStatus
}

# Dot-sourcing exposes the functions for isolated tests without changing this machine.
if ($MyInvocation.InvocationName -ne '.') {
    try { Invoke-StillChange } catch { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }
}
