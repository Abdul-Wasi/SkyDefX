Add-Type -AssemblyName System.Device
$watcher = New-Object System.Device.Location.GeoCoordinateWatcher
$watcher.Start()

$timeout = 30
while (($watcher.Status -ne 'Ready') -and ($watcher.Permission -ne 'Denied') -and ($timeout -gt 0)) {
    Start-Sleep -Milliseconds 100
    $timeout--
}

if ($watcher.Permission -eq 'Denied') {
    Write-Host "ERROR: Permission Denied by Windows settings"
} elseif ($watcher.Position.Location.IsUnknown) {
    Write-Host "ERROR: Location Unknown"
} else {
    $pos = $watcher.Position.Location
    Write-Host "$($pos.Latitude),$($pos.Longitude)"
}
