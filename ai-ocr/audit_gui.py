# -*- coding: utf-8 -*-
"""
audit_gui.py
====================================================
audit_labels.py가 만든 label_audit_candidates.csv를 엑셀로 한 줄씩 보며
new_label 칸을 직접 타이핑하는 대신, 사진을 보면서 단축키 한 번으로
판정하는 빠른 검수용 GUI.

기존 파이프라인과 완전히 호환됩니다:
  - 읽는 파일: ocr_train_data/label_audit_candidates.csv (audit_labels.py 결과물)
  - 쓰는 칸: 그 안의 "new_label(정답이면 여기에 입력)" 칸 (apply_label_fixes.py가 읽는 바로 그 칸)
  - 판정 즉시 자동 저장되므로, 다 끝나면 그대로 apply_label_fixes.py를 실행하면 됨.

단축키
====================================================
    1  : 지금 label(현재)이 맞음            -> new_label 비워둠 (변경 없음)
    2  : predicted(모델 예측)이 맞음         -> new_label = predicted (라벨 오류 수정)
    3  : 직접 입력 -> 아래 텍스트 박스에 정답 입력 후 Enter
    4  : 번호판이 아니거나 도저히 읽을 수 없는 크롭(데이터 파이프라인 결함)
         -> new_label = "DELETE" 로 표시 (apply_label_fixes.py 실행 시 학습셋에서 자동 제거)
    ←  : 이전 항목 (판정 안 하고 이동)
    →  : 다음 항목 (판정 안 하고 이동)
    스페이스바 : 아직 판정 안 한 다음 항목으로 건너뛰기
    Esc : 종료 (저장은 이미 되어 있음)

실행 방법
====================================================
    cd C:\\Users\\user\\Desktop\\project.v4i.yolov8
    conda activate base
    pip install pillow   (이미 설치돼 있으면 생략)
    python audit_gui.py
"""

import csv
import os
import shutil
from datetime import datetime
from pathlib import Path
import tkinter as tk
from tkinter import font as tkfont

from PIL import Image, ImageTk

PROJECT_DIR = Path(r"C:\Users\user\Desktop\project.v4i.yolov8")
DATA_DIR = PROJECT_DIR / "ocr_train_data"
CROPS_DIR = DATA_DIR / "crops"
AUDIT_CSV = DATA_DIR / "label_audit_candidates.csv"

NEW_LABEL_COL = "new_label(정답이면 여기에 입력)"
FIELDNAMES = ["filename", "label(현재)", "predicted(모델 예측)", "confidence", "cer",
              NEW_LABEL_COL, "verdict"]
DELETE_MARK = "DELETE"


def load_rows():
    with open(AUDIT_CSV, "r", encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        r.setdefault(NEW_LABEL_COL, "")
        r.setdefault("verdict", "")
        # 이미 new_label이 채워져 있던 예전 검토 결과는 verdict를 역으로 추정
        if not r["verdict"] and (r[NEW_LABEL_COL] or "").strip():
            r["verdict"] = "DONE(이전기록)"
    return rows


def save_rows(rows):
    tmp = str(AUDIT_CSV) + ".tmp"
    with open(tmp, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        writer.writeheader()
        for r in rows:
            writer.writerow({k: r.get(k, "") for k in FIELDNAMES})
    os.replace(tmp, str(AUDIT_CSV))


class AuditApp:
    def __init__(self, root, rows):
        self.root = root
        self.rows = rows
        self.idx = 0
        for i, r in enumerate(self.rows):
            if not r.get("verdict"):
                self.idx = i
                break

        root.title("번호판 라벨 검수 (audit_gui.py)")
        root.geometry("900x760")

        self.big_font = tkfont.Font(family="Malgun Gothic", size=22)
        self.med_font = tkfont.Font(family="Malgun Gothic", size=15)
        self.small_font = tkfont.Font(family="Malgun Gothic", size=11)

        self.progress_lbl = tk.Label(root, font=self.small_font)
        self.progress_lbl.pack(pady=(10, 0))

        self.img_lbl = tk.Label(root)
        self.img_lbl.pack(pady=10)

        self.fname_lbl = tk.Label(root, font=self.small_font, fg="gray")
        self.fname_lbl.pack()

        self.cur_lbl = tk.Label(root, font=self.big_font, fg="black")
        self.cur_lbl.pack(pady=(10, 0))
        self.pred_lbl = tk.Label(root, font=self.big_font, fg="blue")
        self.pred_lbl.pack()
        self.meta_lbl = tk.Label(root, font=self.small_font, fg="gray")
        self.meta_lbl.pack()

        self.verdict_lbl = tk.Label(root, font=self.med_font, fg="darkgreen")
        self.verdict_lbl.pack(pady=(6, 0))

        entry_frame = tk.Frame(root)
        entry_frame.pack(pady=10)
        tk.Label(entry_frame, text="직접입력(정답):", font=self.small_font).pack(side=tk.LEFT)
        self.entry = tk.Entry(entry_frame, font=self.med_font, width=20)
        self.entry.pack(side=tk.LEFT, padx=5)
        self.entry.bind("<Return>", lambda e: self.set_verdict("CUSTOM", self.entry.get().strip()))

        help_text = (
            "1=현재라벨맞음(변경없음)   2=예측이맞음(라벨오류수정)   3=직접입력(위 박스+Enter)   "
            "4=번호판아님/불량크롭(제거대상)\n"
            "←/→ = 이동(판정없이)   스페이스 = 다음 미판정으로 건너뛰기   Esc = 종료(자동저장됨)"
        )
        tk.Label(root, text=help_text, font=self.small_font, fg="gray", justify=tk.CENTER).pack(pady=(4, 10))

        root.bind("1", lambda e: self.set_verdict("KEEP", ""))
        root.bind("2", lambda e: self.set_verdict("USE_PREDICTED", None))
        root.bind("4", lambda e: self.set_verdict("BAD_CROP", DELETE_MARK))
        root.bind("<Left>", lambda e: self.move(-1))
        root.bind("<Right>", lambda e: self.move(1))
        root.bind("<space>", lambda e: self.next_unreviewed())
        root.bind("<Escape>", lambda e: root.destroy())

        self.photo = None
        self.render()

    def current_row(self):
        return self.rows[self.idx]

    def render(self):
        r = self.current_row()
        n = len(self.rows)
        done = sum(1 for x in self.rows if x.get("verdict"))
        self.progress_lbl.config(text=f"{self.idx + 1} / {n}   (판정완료 {done}/{n})")
        self.fname_lbl.config(text=r["filename"])

        path = CROPS_DIR / r["filename"]
        if path.exists():
            im = Image.open(str(path)).convert("RGB")
            w, h = im.size
            scale = 700 / w
            im = im.resize((700, int(h * scale)), Image.LANCZOS)
            self.photo = ImageTk.PhotoImage(im)
            self.img_lbl.config(image=self.photo, text="")
        else:
            self.photo = None
            self.img_lbl.config(image="", text="[이미지 파일을 찾을 수 없음]\n" + str(path),
                                 font=self.med_font, fg="red")

        self.cur_lbl.config(text=f"현재 라벨: {r['label(현재)']}")
        self.pred_lbl.config(text=f"모델 예측: {r['predicted(모델 예측)']}")
        self.meta_lbl.config(text=f"confidence={r['confidence']}   cer={r['cer']}")

        v = r.get("verdict", "")
        nl = r.get(NEW_LABEL_COL, "")
        if v:
            shown = nl if nl else "(변경없음)"
            self.verdict_lbl.config(text=f"[판정됨: {v} -> {shown}]")
        else:
            self.verdict_lbl.config(text="[미판정]")

        self.entry.delete(0, tk.END)

    def set_verdict(self, verdict, new_label):
        r = self.current_row()
        r["verdict"] = verdict
        if verdict == "KEEP":
            r[NEW_LABEL_COL] = ""
        elif verdict == "USE_PREDICTED":
            r[NEW_LABEL_COL] = r["predicted(모델 예측)"]
        else:
            r[NEW_LABEL_COL] = new_label or ""
        save_rows(self.rows)
        self.next_unreviewed()

    def move(self, delta):
        self.idx = max(0, min(len(self.rows) - 1, self.idx + delta))
        self.render()

    def next_unreviewed(self):
        n = len(self.rows)
        for offset in range(1, n + 1):
            j = (self.idx + offset) % n
            if not self.rows[j].get("verdict"):
                self.idx = j
                self.render()
                return
        self.idx = min(self.idx + 1, n - 1)
        self.render()
        if all(x.get("verdict") for x in self.rows):
            self.verdict_lbl.config(text="[모든 항목 판정 완료! apply_label_fixes.py를 실행하세요]")


def main():
    if not AUDIT_CSV.exists():
        print(f"{AUDIT_CSV} 가 없습니다. 먼저 audit_labels.py를 실행하세요.")
        return
    if not CROPS_DIR.exists():
        print(f"경고: crops 폴더를 찾을 수 없습니다: {CROPS_DIR}")

    # 원본을 한 번 백업 (실수로 판정 잘못 눌렀을 때 되돌릴 수 있게)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = DATA_DIR / f"label_audit_candidates_backup_{ts}.csv"
    shutil.copy(AUDIT_CSV, backup)
    print(f"원본 백업: {backup}")

    rows = load_rows()
    root = tk.Tk()
    app = AuditApp(root, rows)
    root.mainloop()


if __name__ == "__main__":
    main()
