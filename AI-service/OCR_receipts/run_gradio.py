"""
Gradio 데모: 영수증 이미지 업로드 → OCR + line_grouping + 파싱 결과 확인
실행: pip install gradio && python run_gradio.py
"""
from pathlib import Path

try:
    import gradio as gr
except ImportError:
    print("Gradio가 필요합니다: pip install gradio")
    raise

from src.parse.pipeline import process_ocr_lines_parsed


def run_ocr(image_file):
    if image_file is None:
        return "이미지를 업로드해 주세요.", "", ""
    path = Path(image_file)
    if not path.exists():
        return "파일을 찾을 수 없습니다.", "", ""
    image_bytes = path.read_bytes()
    filename = path.name

    raw_json, lines_json, parsed_json = process_ocr_lines_parsed(image_bytes, filename)

    # line_grouping 결과를 읽기 쉽게
    lines = lines_json.get("lines", [])
    line_texts = "\n".join(
        f"{i+1}. {ln.get('line_text', '')}" for i, ln in enumerate(lines)
    )
    lines_summary = f"[line_grouping] 총 {len(lines)}줄\n\n{line_texts}"

    parsed_str = (
        f"가맹점: {parsed_json.get('merchant')}\n"
        f"날짜: {parsed_json.get('date')} / {parsed_json.get('datetime')}\n"
        f"금액: {parsed_json.get('total')}\n"
        f"항목: {parsed_json.get('items')}"
    )

    import json
    raw_str = json.dumps(raw_json, ensure_ascii=False, indent=2)

    return lines_summary, parsed_str, raw_str


def main():
    with gr.Blocks(title="OCR 영수증 line_grouping 데모") as demo:
        gr.Markdown("## 영수증 OCR + line_grouping 결과 보기")
        with gr.Row():
            img = gr.Image(type="filepath", label="영수증 이미지")
        btn = gr.Button("OCR 실행")
        with gr.Row():
            out_lines = gr.Textbox(
                label="line_grouping 결과 (줄별 텍스트)",
                lines=20,
                max_lines=30,
            )
        with gr.Row():
            out_parsed = gr.Textbox(label="파싱 결과 (가맹점/날짜/금액)", lines=8)
        with gr.Accordion("raw_json (펼치기)", open=False):
            out_raw = gr.Textbox(label="raw_json", lines=15)

        btn.click(
            fn=run_ocr,
            inputs=img,
            outputs=[out_lines, out_parsed, out_raw],
        )

    demo.launch(server_name="0.0.0.0", server_port=7860)


if __name__ == "__main__":
    main()
