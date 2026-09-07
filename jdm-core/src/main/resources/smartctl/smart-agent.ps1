param(
    [Parameter(Mandatory)][string]$SmartctlPath,
    [Parameter(Mandatory)][string]$IpcDir
)

$ErrorActionPreference = 'Continue'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

Remove-Item (Join-Path $IpcDir 'smart-agent-stop.txt') -Force -ErrorAction SilentlyContinue
[System.IO.File]::WriteAllText((Join-Path $IpcDir 'smart-agent-ready.txt'), 'ready', $utf8NoBom)

while ($true) {
    if (Test-Path (Join-Path $IpcDir 'smart-agent-stop.txt')) { break }
    $reqs = Get-ChildItem (Join-Path $IpcDir 'smart-req-*.txt') -ErrorAction SilentlyContinue
    foreach ($req in $reqs) {
        $device = (Get-Content $req.FullName -Raw -ErrorAction SilentlyContinue).Trim()
        Remove-Item $req.FullName -Force -ErrorAction SilentlyContinue
        if (-not $device) { continue }
        $outFile     = Join-Path $IpcDir "smart-ipc-$device.json"
        $statFile    = Join-Path $IpcDir "smart-ipc-$device.status"
        $written     = $false
        $fallbackOut = $null

        # Pass 1 - simple paths
        foreach ($d in @("/dev/$device", $device)) {
            $out  = & $SmartctlPath --json -a $d 2>&1
            $code = $LASTEXITCODE
            $text = ($out | ForEach-Object { $_.ToString() }) -join "`n"
            if (-not $text.TrimStart().StartsWith('{')) { continue }
            if (($code -band 2) -ne 0) { if ($null -eq $fallbackOut) { $fallbackOut = $out }; continue }
            [System.IO.File]::WriteAllText($outFile, $text, $utf8NoBom)
            $written = $true; break
        }

        # Pass 2 - Win32 path with NVMe/SAT hints
        if (-not $written -and $device -match '^pd(\d+)$') {
            $win32 = "\\.\PhysicalDrive$($Matches[1])"
            foreach ($hint in @('', '-d nvme', '-d sat')) {
                $args2 = @('--json', '-a', $win32)
                if ($hint) { $args2 += $hint.Split(' ') }
                $out  = & $SmartctlPath @args2 2>&1
                $code = $LASTEXITCODE
                $text = ($out | ForEach-Object { $_.ToString() }) -join "`n"
                if (-not $text.TrimStart().StartsWith('{')) { continue }
                if (($code -band 2) -ne 0) { if ($null -eq $fallbackOut) { $fallbackOut = $out }; continue }
                [System.IO.File]::WriteAllText($outFile, $text, $utf8NoBom)
                $written = $true; break
            }
        }

        # Fallback - use first error-JSON so UI has drive identity
        if (-not $written -and ($null -ne $fallbackOut)) {
            $text = ($fallbackOut | ForEach-Object { $_.ToString() }) -join "`n"
            [System.IO.File]::WriteAllText($outFile, $text, $utf8NoBom); $written = $true
        }
        if (-not $written) {
            [System.IO.File]::WriteAllText($statFile, 'no-json: all candidates failed', $utf8NoBom)
        }
    }
    Start-Sleep -Milliseconds 100
}
