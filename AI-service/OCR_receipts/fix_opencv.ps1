# paddleocr이 끌어온 opencv-python 4.6 / opencv-contrib 4.6 제거
# (numpy 1.x ABI용이라 numpy 2와 충돌 → import cv2 시 RuntimeError)
# opencv-python-headless 4.13만 남기면 됨.
& "$PSScriptRoot\venv\Scripts\pip.exe" uninstall opencv-python opencv-contrib-python -y
