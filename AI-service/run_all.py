#!/usr/bin/env python3
"""
SmartBudget Python 서비스 통합 실행 스크립트

- ml-server (FastAPI): OCR/분류/LLM/RAG + Recommendation Flask 마운트 → 포트 8000
- OCR_receipts: PaddleOCR 기반 영수증 OCR → 8000/receipts 로 마운트 (별도 포트 없음)

실행: AI-service 폴더에서
  python run_all.py

백엔드 설정 (application.properties 또는 환경변수):
  ml.server.url=http://localhost:8000
  recommendation.ai.server.url=http://localhost:8000/recommendation
  ocr.receipts.server.url=http://localhost:8000/receipts
"""
import os
import sys
import signal
import subprocess
import time
from pathlib import Path
from dotenv import load_dotenv
import urllib.request
import urllib.error
import threading

PROJECT_ROOT = Path(__file__).resolve().parent
ML_SERVER_DIR = PROJECT_ROOT / "ml-server"
OCR_RECEIPTS_DIR = PROJECT_ROOT / "OCR_receipts"
VENV_DIR = PROJECT_ROOT / ".venv310"
LOG_DIR = PROJECT_ROOT / "logs"

processes = []
_log_files = []


def get_python_executable():
    """가상환경이 있으면 사용, 없으면 현재 Python 사용"""
    venv_python = VENV_DIR / "Scripts" / "python.exe"  # Windows
    if venv_python.exists():
        return str(venv_python)
    venv_python_unix = VENV_DIR / "bin" / "python"  # Linux/Mac
    if venv_python_unix.exists():
        return str(venv_python_unix)
    return sys.executable


def kill_children():
    for p in processes:
        if p.poll() is None:
            p.terminate()
    for p in processes:
        try:
            p.wait(timeout=5)
        except subprocess.TimeoutExpired:
            p.kill()
    processes.clear()
    for f in _log_files:
        try:
            f.close()
        except Exception:
            pass
    _log_files.clear()


def wait_for_health(url: str, *, name: str, timeout_s: float = 90.0, interval_s: float = 0.5) -> bool:
    """서비스 /health 가 200이 될 때까지 대기"""
    deadline = time.time() + timeout_s
    last_err: Exception | None = None

    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=2) as resp:
                if 200 <= getattr(resp, "status", 200) < 300:
                    return True
        except Exception as e:
            last_err = e
        time.sleep(interval_s)

    print(f"{name} 준비 실패: {url}", file=sys.stderr)
    if last_err is not None:
        print(f"  마지막 오류: {type(last_err).__name__}: {last_err}", file=sys.stderr)
    return False


def check_python_imports(python_exe: str, modules: list[str]) -> bool:
    """선택된 python(가상환경 포함)에 필수 모듈이 설치돼있는지 사전 점검.
    - 특히 FastAPI의 UploadFile/File(...)는 python-multipart(모듈명 multipart)가 없으면 즉시 실패함.
    """
    missing: list[str] = []
    for m in modules:
        try:
            r = subprocess.run(
                [python_exe, "-c", f"import {m}"],
                cwd=str(PROJECT_ROOT),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
            if r.returncode != 0:
                missing.append(m)
        except Exception:
            missing.append(m)

    if missing:
        print("=" * 60, file=sys.stderr)
        print("Python 의존성 누락으로 AI-service 실행이 실패합니다.", file=sys.stderr)
        print(f"- 사용 중인 Python: {python_exe}", file=sys.stderr)
        print(f"- 누락 추정 모듈: {', '.join(missing)}", file=sys.stderr)
        print("", file=sys.stderr)
        print("해결 방법:", file=sys.stderr)
        if sys.platform == "win32":
            print(r"  1) AI-service 폴더에서 .\run_with_py310.ps1 실행(권장)", file=sys.stderr)
            print(r"     (자동으로 .venv310 만들고 requirements.txt 설치함)", file=sys.stderr)
            print(r"  또는", file=sys.stderr)
            print(r"  2) .\.venv310\Scripts\pip.exe install -r requirements.txt", file=sys.stderr)
        else:
            print("  pip install -r requirements.txt", file=sys.stderr)
        print("", file=sys.stderr)
        print("참고: multipart 에러는 보통 'python-multipart'가 설치되지 않았다는 뜻입니다.", file=sys.stderr)
        print("      (패키지명: python-multipart, import 모듈명: multipart)", file=sys.stderr)
        print("=" * 60, file=sys.stderr)
        return False

    return True


def _tee_stream(stream, *, prefix: str, out_console, out_file):
    """subprocess stdout/stderr를 콘솔 + 파일에 동시에 기록"""
    try:
        for line in iter(stream.readline, ""):
            if not line:
                break
            msg = f"[{prefix}] {line}"
            try:
                out_console.write(msg)
                out_console.flush()
            except Exception:
                pass
            try:
                out_file.write(msg)
                out_file.flush()
            except Exception:
                pass
    finally:
        try:
            stream.close()
        except Exception:
            pass


def spawn_with_logs(*, name: str, cmd: list[str], cwd: Path, env: dict) -> subprocess.Popen:
    """프로세스를 띄우고 logs/에 stdout+stderr를 남김(콘솔에도 같이 출력)"""
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    ts = time.strftime("%Y%m%d_%H%M%S")
    stdout_path = LOG_DIR / f"{ts}_{name}_stdout.log"
    stderr_path = LOG_DIR / f"{ts}_{name}_stderr.log"

    # encoding을 고정해 한글/깨짐을 줄임
    p = subprocess.Popen(
        cmd,
        cwd=str(cwd),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )

    f_out = stdout_path.open("a", encoding="utf-8", errors="replace")
    f_err = stderr_path.open("a", encoding="utf-8", errors="replace")
    _log_files.extend([f_out, f_err])

    t1 = threading.Thread(
        target=_tee_stream,
        args=(p.stdout,),
        kwargs={"prefix": f"{name}:stdout", "out_console": sys.stdout, "out_file": f_out},
        daemon=True,
    )
    t2 = threading.Thread(
        target=_tee_stream,
        args=(p.stderr,),
        kwargs={"prefix": f"{name}:stderr", "out_console": sys.stderr, "out_file": f_err},
        daemon=True,
    )
    t1.start()
    t2.start()

    print(f"[{name}] pid={p.pid}")
    print(f"[{name}] stdout log: {stdout_path}")
    print(f"[{name}] stderr log: {stderr_path}")
    return p


def main():
    if not ML_SERVER_DIR.is_dir():
        print("ml-server 폴더를 찾을 수 없습니다.", file=sys.stderr)
        sys.exit(1)
    
    python_exe = get_python_executable()
    if python_exe == sys.executable:
        venv_python = VENV_DIR / "Scripts" / "python.exe"
        venv_python_unix = VENV_DIR / "bin" / "python"
        if not venv_python.exists() and not venv_python_unix.exists():
            print("=" * 60, file=sys.stderr)
            print("경고: .venv310 가상환경이 없습니다!", file=sys.stderr)
            print("=" * 60, file=sys.stderr)
            print("\n다른 사람이 Git에서 받은 경우, 다음을 실행하세요:\n", file=sys.stderr)
            if sys.platform == "win32":
                print("  Windows (PowerShell): .\\run_with_py310.ps1", file=sys.stderr)
                print("  Windows (CMD):        run_with_py310.bat", file=sys.stderr)
            else:
                print("  python3.10 -m venv .venv310", file=sys.stderr)
                print("  source .venv310/bin/activate", file=sys.stderr)
                print("  pip install -r requirements.txt", file=sys.stderr)
            print("\n자세한 내용은 README.md를 참고하세요.", file=sys.stderr)
            print(f"\n현재: 시스템 Python ({sys.executable}) 사용 중", file=sys.stderr)
            print("      → 의존성이 설치되지 않으면 실행이 실패할 수 있습니다.\n", file=sys.stderr)
    else:
        print(f"가상환경 사용: {python_exe}\n")

    # 사전 점검: fastapi/uvicorn + multipart(=python-multipart) 없으면 8000/8001이 뜨다가 바로 죽음
    if not check_python_imports(python_exe, ["fastapi", "uvicorn", "multipart"]):
        sys.exit(1)

    def on_signal(signum, frame):
        print("\n종료 중...")
        kill_children()
        sys.exit(0)

    signal.signal(signal.SIGINT, on_signal)
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, on_signal)

    # 환경 변수 로드 (.env 파일에서)
    env_file = PROJECT_ROOT / ".." / "backend" / "smartbudget" / ".env"
    if env_file.exists():
        load_dotenv(env_file)
        print(f"환경 변수 로드: {env_file}")
    
    env = os.environ.copy()
    # 통합 실행 기본: OCR_receipts를 8000 하위(/receipts)로 마운트하여 사용
    env.setdefault("OCR_PORT", "8000")
    
    # UTF-8 인코딩 설정
    env.setdefault("PYTHONIOENCODING", "utf-8")
    env.setdefault("PYTHONUTF8", "1")
    
    # GEMINI_API_KEY 확인
    if "GEMINI_API_KEY" in env:
        print(f"GEMINI_API_KEY 설정됨: {env['GEMINI_API_KEY'][:10]}...")
    else:
        print("경고: GEMINI_API_KEY가 환경 변수에 없습니다.")

    # 1) ml-server (포트 8000, Recommendation 포함)
    print("ml-server 기동 중 (포트 8000, Recommendation 마운트)...")
    p1 = spawn_with_logs(
        name="ml_server_8000",
        cmd=[python_exe, "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"],
        cwd=ML_SERVER_DIR,
        env=env,
    )
    processes.append(p1)
    time.sleep(0.5)
    if p1.poll() is not None:
        print("ml-server 기동 실패(프로세스가 즉시 종료됨)", file=sys.stderr)
        kill_children()
        sys.exit(1)
    if not wait_for_health("http://127.0.0.1:8000/health", name="ml-server(8000)", timeout_s=90):
        kill_children()
        sys.exit(1)

    # 2) OCR_receipts는 ml-server(8000)에 /receipts 로 마운트되어 함께 기동됨
    #    → 별도 8001 프로세스를 띄우지 않음 (포트 죽음/ConnectException 방지)
    if not wait_for_health("http://127.0.0.1:8000/receipts/health", name="OCR_receipts(/receipts)", timeout_s=120):
        kill_children()
        sys.exit(1)

    print("")
    print("=== Python 서비스 통합 실행 중 ===")
    print("  ml-server:       http://localhost:8000")
    print("  Recommendation:  http://localhost:8000/recommendation")
    print("  OCR_receipts:    http://localhost:8000/receipts")
    print("종료하려면 Ctrl+C")
    print("")

    # 한쪽이 죽으면 다른 쪽도 같이 내리고 실패로 종료(조용히 죽어서 백엔드만 ConnectException 나는 상황 방지)
    try:
        while True:
            for p in processes:
                code = p.poll()
                if code is not None:
                    print(f"하위 프로세스 종료 감지(pid={p.pid}, exit_code={code}). 전체 종료합니다.", file=sys.stderr)
                    kill_children()
                    sys.exit(code if code != 0 else 1)
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\n종료 중...")
        kill_children()
        sys.exit(0)


if __name__ == "__main__":
    main()
