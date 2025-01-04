
# Drive letter is passed in
$driveLetter = $($args[0])

# Add ":" to the drive letter if it's missing
if (-not $driveLetter.EndsWith(':')) {
  $driveLetter += ':'
}

# Get disk information using Get-CimInstance
$disk = Get-CimInstance -ClassName Win32_LogicalDisk -Filter "DeviceID='$driveLetter'"

# Calculate disk space values in GB
$totalSpaceGB = [Math]::Round($disk.Size / 1GB, 2)
$freeSpaceGB = [Math]::Round($disk.FreeSpace / 1GB, 2)
$usedSpaceGB = [Math]::Round(($disk.Size - $disk.FreeSpace) / 1GB, 2)

# Display the results
Write-Host "Drive: $driveLetter"
Write-Host "TotalSpaceGb=$totalSpaceGB"
Write-Host "FreeSpaceGb=$freeSpaceGB"
Write-Host "UsedSpaceGb=$usedSpaceGB"