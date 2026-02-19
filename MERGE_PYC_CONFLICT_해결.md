# .pyc merge 충돌 해결 방법

`__pycache__/*.pyc` 는 Python이 자동 생성하는 파일이라 Git에서 고칠 필요 없고, **한쪽 버전으로만 정리**하면 됩니다.

---

## 1. 왜 막히는지

- `.pyc` 파일이 **에디터(Cursor)에서 열려 있으면** Windows가 파일을 잠가서 Git이 수정하지 못합니다.
- `unable to unlink ... Invalid argument` → 그 파일을 쓰는 프로그램을 닫아야 합니다.

---

## 2. 해결 순서 (추천)

1. **Cursor에서 아래 파일 탭이 열려 있으면 모두 닫기**
   - `OCR_receipts/src/.../__pycache__/rule_seed.cpython-310.pyc`
   - `OCR_receipts/src/.../__pycache__/receipt_mapping.cpython-310.pyc`
   - `OCR_receipts/src/.../__pycache__/run_ocr_to_json.cpython-310.pyc`

2. **PowerShell을 새로 열고** (smartbudget 폴더에서):
   ```powershell
   cd D:\VScode\smartBudget\smartbudget
   .\resolve_pyc_merge.ps1
   ```

3. **이후 병합 커밋**:
   ```powershell
   git commit -m "Merge branch han into main; resolve pycache conflicts"
   ```

---

## 3. 스크립트 대신 직접 할 때

```powershell
cd D:\VScode\smartBudget\smartbudget
# lock 있으면 제거
Remove-Item .git\index.lock -Force -ErrorAction SilentlyContinue

# han 쪽 버전으로 채우고 스테이징
git checkout --theirs "OCR_receipts/src/ml/__pycache__/rule_seed.cpython-310.pyc"
git add "OCR_receipts/src/ml/__pycache__/rule_seed.cpython-310.pyc"
git checkout --theirs "OCR_receipts/src/parse/__pycache__/receipt_mapping.cpython-310.pyc"
git add "OCR_receipts/src/parse/__pycache__/receipt_mapping.cpython-310.pyc"
git checkout --theirs "OCR_receipts/src/parse/__pycache__/run_ocr_to_json.cpython-310.pyc"
git add "OCR_receipts/src/parse/__pycache__/run_ocr_to_json.cpython-310.pyc"

git commit -m "Merge branch han into main; resolve pycache conflicts"
```

---

## 4. 앞으로 .pyc가 안 올라가게 하려면

`.gitignore`에 이미 `__pycache__/` 와 `*.pyc` 가 있으므로, 한 번 저장소에서만 제거해 두면 됩니다 (병합 커밋 후에 해도 됨):

```powershell
git rm -r --cached OCR_receipts/src/__pycache__ 2>$null
git rm -r --cached OCR_receipts/src/api/__pycache__ 2>$null
git rm -r --cached OCR_receipts/src/ml/__pycache__ 2>$null
git rm -r --cached OCR_receipts/src/parse/__pycache__ 2>$null
git commit -m "Stop tracking __pycache__"
```

이후에는 Python이 다시 만들어도 Git에 안 올라갑니다.
