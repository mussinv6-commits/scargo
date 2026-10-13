import os
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

from ultralytics import YOLO

if __name__ == "__main__":  # Windows에서 필수
    # 지금까지 학습 중 가장 좋은 결과(mAP50-95=0.717)를 낸 run-9의 best.pt 사용
    model = YOLO(r"C:\Users\user\Desktop\project.v4i.yolov8\runs\detect\plate_detect_strict-9\weights\best.pt")

    source_dir = r"C:\Users\user\Desktop\화물차 번호판 사진"

    results = model.predict(
        source=source_dir,
        imgsz=960,
        conf=0.25,          # 이 확신도 이상 예측만 라벨로 채택. 오탐 많으면 0.35~0.4로 올리기
        save=True,           # 박스 그려진 이미지도 같이 저장 (검수용)
        save_txt=True,       # YOLO 포맷 txt 라벨 저장
        save_conf=False,
        project=r"C:\Users\user\Desktop\화물차 번호판 사진",
        name="auto_labels",
        exist_ok=True,
        device=0,             # GPU 없으면 "cpu"
    )

    total = len(results)
    no_detection = [r.path for r in results if len(r.boxes) == 0]

    print(f"\n전체 처리: {total}장")
    print(f"라벨(번호판) 못 찾은 이미지: {len(no_detection)}장")

    if no_detection:
        list_path = os.path.join(r"C:\Users\user\Desktop\화물차 번호판 사진", "no_detection_list.txt")
        with open(list_path, "w", encoding="utf-8") as f:
            for p in no_detection:
                f.write(p + "\n")
        print(f"미검출 이미지 목록 저장: {list_path}")
