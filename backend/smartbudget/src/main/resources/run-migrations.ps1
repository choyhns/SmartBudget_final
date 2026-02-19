# SQL 스키마/마이그레이션 순차 실행
# 사용법: .\run-migrations.ps1
# 또는: .\run-migrations.ps1 -DbHost localhost -Port 5432 -User postgres -Database projectdb
# PGPASSWORD는 환경변수로 설정하거나 아래에서 입력받도록 수정

param(
    [string]$DbHost = "localhost",
    [string]$Port = "5432",
    [string]$User = $env:DB_USERNAME,
    [string]$Database = "projectdb",
    [string]$Password = $env:DB_PASSWORD,
    [string]$PsqlPath = ""   # 비어 있으면 자동 검색. 예: "C:\Program Files\PostgreSQL\16\bin\psql.exe"
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot

# psql 경로 찾기 (PATH에 없으면 Windows 기본 설치 경로 검색)
$psqlExe = $PsqlPath
if (-not $psqlExe) {
    try { $psqlExe = (Get-Command psql -ErrorAction Stop).Source } catch {}
}
if (-not $psqlExe) {
    $pgPaths = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue
    if ($pgPaths) { $psqlExe = $pgPaths | Sort-Object { $_.FullName } -Descending | Select-Object -First 1 -ExpandProperty FullName }
}
if (-not $psqlExe -or -not (Test-Path $psqlExe)) {
    Write-Error "psql을 찾을 수 없습니다. PostgreSQL이 설치되어 있는지 확인하고, PATH에 bin 폴더를 추가하거나 -PsqlPath 로 경로를 지정하세요. 예: -PsqlPath 'C:\Program Files\PostgreSQL\16\bin\psql.exe'"
    exit 1
}

# pgvector 관련 SQL은 삭제됨. 아래 순서대로 실행 (Chroma/JSONB 환경용)
$files = @(
    "schema_rag_jsonb.sql",
    "schema_question_cards_jsonb.sql",
    "schema_saving_goals_goal_title.sql",
    "data_question_cards.sql"
)

if (-not $Password) {
    $sec = Read-Host "DB password" -AsSecureString
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}

$env:PGPASSWORD = $Password

foreach ($f in $files) {
    $path = Join-Path $scriptDir $f
    if (-not (Test-Path $path)) {
        Write-Warning "Skip (not found): $f"
        continue
    }
    Write-Host "Running: $f"
    & $psqlExe -h $DbHost -p $Port -U $User -d $Database -f $path
    if ($LASTEXITCODE -ne 0) { throw "Failed: $f" }
}

Write-Host "Done."
