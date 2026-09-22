param([string]$DotnetPath = 'dotnet')
$ErrorActionPreference = 'Stop'
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$project = Join-Path $PSScriptRoot 'Still.Windows.csproj'
foreach ($rid in @('win-x64', 'win-arm64')) {
    $architecture = $rid.Substring(4)
    $output = Join-Path $repo "dist\Still-Windows-$architecture"
    & $DotnetPath publish $project -c Release -r $rid --self-contained true -p:PublishSingleFile=true -p:DebugType=None -o $output
    if ($LASTEXITCODE -ne 0) { throw "Build failed for $rid" }
    Copy-Item -LiteralPath (Join-Path $repo 'platforms\README.md') -Destination (Join-Path $output 'READ-ME.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'DnsControl.ps1') -Destination $output
    $archive = Join-Path $repo "dist\Still-Windows-$architecture.zip"
    # Include the portable runtime DLLs, but not intermediate/debug artifacts.
    $files = @(Get-ChildItem -LiteralPath $output -File | Where-Object { $_.Extension -ne '.pdb' } | ForEach-Object { $_.FullName })
    Compress-Archive -LiteralPath $files -DestinationPath $archive -Force
    Write-Output $archive
}
