import os
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

from ultralytics import YOLO

# 1차(conf=0.25) + 2차(conf=0.05, imgsz=960)에서도 검출 실패한 38장
# -> 원본 확인해보니 번호판이 실제로는 잘 보이는데 모델이 못 잡은 경우가 많음
# -> imgsz를 키워서(작은 객체 인식력 개선) + augment(TTA)로 마지막 시도
MISSING = [
    '006너8562.jpeg', '006더6447.jpeg', '006더7108.jpeg', '경기80아2186.jpeg', '경기80자2065.jpeg',
    '경기84바8168.jpeg', '경기85바5047.jpeg', '경기85아4240.jpeg', '경기91아7910.jpeg', '경남06모6426.jpeg',
    '경남06모7917.jpeg', '경남06소6333.jpeg', '경남06소6486.jpeg', '경남14노5426.jpeg', '경남14노5533.jpeg',
    '경남14노7722.jpeg', '경남14노7724.jpeg', '경남14노7915.jpeg', '경남14노8462.jpeg', '경남14노8729.jpeg',
    '경남14노8741.jpeg', '경남80아9472.jpeg', '경남81사1894.jpeg', '경남82사8580.jpeg', '경남99사2126.jpeg',
    '경북87아5842.jpeg', '대구81바6683.jpeg', '대구81아5363.jpeg', '대구86아1076.jpeg', '부남80두2084.jpeg',
    '부산80노1122.jpeg', '부산80두3869.jpeg', '부산80루9010.jpeg', '부산86누1017.jpeg', '부산91자1611.jpeg',
    '서울89자9584.jpeg', '전남87바5931.jpeg', '충북90아9211.jpeg',
]

if __name__ == "__main__":  # Windows에서 필수
    model = YOLO(r"C:\Users\user\Desktop\project.v4i.yolov8\runs\detect\plate_detect_strict-9\weights\best.pt")
    src_dir = r"C:\Users\user\Desktop\화물차 번호판 사진"
    paths = [os.path.join(src_dir, name) for name in MISSING]

    results = model.predict(
        source=paths,
        imgsz=1536,           # 960 -> 1536, 트럭 전체가 찍혀 번호판이 작게 나온 사진 대비
        conf=0.05,
        augment=True,          # 테스트타임 증강(TTA)으로 검출력 최대한 끌어올림
        save=True,
        save_txt=True,
        save_conf=True,
        project=src_dir,
        name="recheck_labels2",
        exist_ok=True,
        device=0,              # GPU 없으면 "cpu"
    )

    still_missing = []
    for r in results:
        name = os.path.basename(r.path)
        if len(r.boxes) == 0:
            still_missing.append(name)
        else:
            confs = r.boxes.conf.tolist()
            print(f"{name}: {len(confs)}개 검출, 최고 확신도 {max(confs):.3f}")

    print(f"\n38장 중 여전히 미검출: {len(still_missing)}장")
    if still_missing:
        list_path = os.path.join(src_dir, "still_no_detection2.txt")
        with open(list_path, "w", encoding="utf-8") as f:
            for n in still_missing:
                f.write(n + "\n")
        print("목록 저장:", list_path)
