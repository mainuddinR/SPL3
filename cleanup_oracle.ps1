$ErrorActionPreference = 'SilentlyContinue'

Write-Host "Stopping Oracle Services..."
Stop-Service -Name "Oracle*" -Force

Write-Host "Attempting MSI uninstall..."
Start-Process msiexec.exe -ArgumentList "/X{C220B7FD-3095-47FC-A0C0-AE49DE6E320A} /qn" -Wait

Write-Host "Removing leftover Oracle Services..."
$services = @("OracleJobSchedulerXE", "OracleOraDB21Home1MTSRecoveryService", "OracleOraDB21Home1TNSListener", "OracleServiceXE", "OracleVssWriterXE")
foreach ($svc in $services) {
    sc.exe delete $svc
}

Write-Host "Removing Registry Keys..."
Remove-Item -Path "HKLM:\Software\Oracle" -Recurse -Force
Remove-Item -Path "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{C220B7FD-3095-47FC-A0C0-AE49DE6E320A}" -Force

Write-Host "Cleaning up file system..."
Remove-Item -Path "C:\app\Mainuddin\product\21c" -Recurse -Force

Write-Host "Cleanup complete. Please return to the IDE."
