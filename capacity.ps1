
# Drive letter is passed in
$driveLetter = $args[0]

if ($driveLetter -eq $null) {
    Write-Output "WARNING: drive not provided - defaulting to c: for test"
    $driveLetter = "C:"
}

# Add ":" to the drive letter if it's missing
if (-not $driveLetter.EndsWith(':')) {
  $driveLetter += ':'
}

# Get disk information using Get-CimInstance
$disk = Get-CimInstance -ClassName Win32_LogicalDisk -Filter "DeviceID='$driveLetter'"

# Calculate disk space values in GB
$totalSpaceGb = [Math]::Round($disk.Size / 1GB, 2)
$freeSpaceGb = [Math]::Round($disk.FreeSpace / 1GB, 2)
$usedSpaceGb = [Math]::Round(($disk.Size - $disk.FreeSpace) / 1GB, 2)

# Create a hashtable with the results
$diskUsage = @{
    Drive = $driveLetter
    TotalSpaceGb = $totalSpaceGb
    FreeSpaceGb = $freeSpaceGb
    UsedSpaceGb = $usedSpaceGb
}

# Convert the hashtable to JSON
$jsonOutput = $diskUsage | ConvertTo-Json

# Output the JSON
Write-Output $jsonOutput