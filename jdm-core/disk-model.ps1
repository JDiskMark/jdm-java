# This powershell script is used to map drive letters to disk model numbers
# It is intended to be called by Runtime.getRuntime().exec(...)

Get-WmiObject Win32_DiskDrive | % {
  $disk = $_
  $partitions = "ASSOCIATORS OF " +
                "{Win32_DiskDrive.DeviceID='$($disk.DeviceID)'} " +
                "WHERE AssocClass = Win32_DiskDriveToDiskPartition"
  Get-WmiObject -Query $partitions | % {
    $partition = $_
    $drives = "ASSOCIATORS OF " +
              "{Win32_DiskPartition.DeviceID='$($partition.DeviceID)'} " +
              "WHERE AssocClass = Win32_LogicalDiskToPartition"
    Get-WmiObject -Query $drives | % {
      New-Object -Type PSCustomObject -Property @{
        #Disk        = $disk.DeviceID
        #DiskSize    = $disk.Size
        DiskModel   = $disk.Model
        #Partition   = $partition.Name
        #RawSize     = $partition.Size
        DriveLetter = $_.DeviceID
        #VolumeName  = $_.VolumeName
        #Size        = $_.Size
        #FreeSpace   = $_.FreeSpace
      }
    }
  }
}