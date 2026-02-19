# __pycache__ .pyc merge conflict 해결 스크립트
# 사용법: Cursor에서 해당 .pyc 파일 탭을 닫고, PowerShell에서 이 스크립트 실행

$repo = "D:\VScode\smartBudget\smartbudget"
Set-Location $repo

# 1. lock 제거
if (Test-Path ".git\index.lock") { Remove-Item ".git\index.lock" -Force }

# 2. 충돌 난 .pyc를 "han 쪽 버전"으로 채우고 스테이징
$pyc = @(
    "OCR_receipts/src/ml/__pycache__/rule_seed.cpython-310.pyc",
    "OCR_receipts/src/parse/__pycache__/receipt_mapping.cpython-310.pyc",
    "OCR_receipts/src/parse/__pycache__/run_ocr_to_json.cpython-310.pyc"
)
foreach ($f in $pyc) {
    git checkout --theirs $f
    git add $f
}

Write-Host "충돌 해결 완료. 이제 git commit 하세요:"
Write-Host "  git commit -m `"Merge branch han into main; resolve pycache conflicts`""
