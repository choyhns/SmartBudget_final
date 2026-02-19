# 8000(ml-server), 8001 포트 사용 중인 프로세스 종료 (OCR은 8000/receipts 사용, 8001은 레거시)
$ports = @(8000, 8001)
$pidsToKill = @{}
foreach ($port in $ports) {
    $lines = netstat -ano | Select-String ":$port\s"
    foreach ($line in $lines) {
        if ($line -match "\s+(\d+)\s*$") {
            $procId = $matches[1].Trim()
            if ($procId -ne "0") { $pidsToKill[$procId] = $true }
        }
    }
}
foreach ($procId in $pidsToKill.Keys) {
    Write-Host "PID $procId is killing..."
    & taskkill /PID $procId /F 2>$null
}
Write-Host "complete. retry python run_all.py."
