import os
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

from ultralytics import YOLO

# 1차 자동 라벨링(conf=0.25)에서 검출 실패한 83장
MISSING = [
    '006너8562.jpeg', '006너9189.jpeg', '006더6447.jpeg', '006더7108.jpeg', '006더8140.jpeg',
    '경기80아2186.jpeg', '경기80자2065.jpeg', '경기82바8326.jpeg', '경기84바8168.jpeg', '경기85바5013.jpeg',
    '경기85바5047.jpeg', '경기85아4240.jpeg', '경기86바2701.jpeg', '경기87바8517.jpeg', '경기88사8040.jpeg',
    '경기91아7910.jpeg', '경기91아9214.jpeg', '경기92아1253.jpeg', '경기95자9162.jpeg', '경남06모6426.jpeg',
    '경남06모6745.jpeg', '경남06모7643.jpeg', '경남06모7783.jpeg', '경남06모7917.jpeg', '경남06소6333.jpeg',
    '경남06소6486.jpeg', '경남14고8874.jpeg', '경남14노5426.jpeg', '경남14노5533.jpeg', '경남14노6187.jpeg',
    '경남14노6692.jpeg', '경남14노6787.jpeg', '경남14노7036.jpeg', '경남14노7607.jpeg', '경남14노7682.jpeg',
    '경남14노7722.jpeg', '경남14노7724.jpeg', '경남14노7883.jpeg', '경남14노7915.jpeg', '경남14노8283.jpeg',
    '경남14노8462.jpeg', '경남14노8729.jpeg', '경남14노8741.jpeg', '경남80아9472.jpeg', '경남81사1894.jpeg',
    '경남81아1308.jpeg', '경남82사5270.jpeg', '경남82사5922.jpeg', '경남82사6125.jpeg', '경남82사8580.jpeg',
    '경남82아2822.jpeg', '경남82아7500.jpeg', '경남99바2220.jpeg', '경남99사2126.jpeg', '경북86바3350.jpeg',
    '경북86아7724.jpeg', '경북87아5842.jpeg', '경북88자1400.jpeg', '광주85사2116.jpeg', '대구80아2512.jpeg',
    '대구80아5743.jpeg', '대구80자1338.jpeg', '대구81바6237.jpeg', '대구81바6683.jpeg', '대구81아5363.jpeg',
    '대구86아1076.jpeg', '부남80두2084.jpeg', '부산80노1122.jpeg', '부산80누1017.jpeg', '부산80두3869.jpeg',
    '부산80러4819.jpeg', '부산80루9010.jpeg', '부산81가1761.jpeg', '부산86누1017.jpeg', '부산90배4277.jpeg',
    '부산90배7584.jpeg', '부산91자1611.jpeg', '서울89자9584.jpeg', '전남80바2849.jpeg', '전남81사4151.jpeg',
    '전남87바5931.jpeg', '전남91자1346.jpeg', '충북90아9211.jpeg',
]

if __name__ == "__main__":  # Windows에서 필수
    model = YOLO(r"C:\Users\user\Desktop\project.v4i.yolov8\runs\detect\plate_detect_strict-9\weights\best.pt")
    src_dir = r"C:\Users\user\Desktop\화물차 번호판 사진"
    paths = [os.path.join(src_dir, name) for name in MISSING]

    results = model.predict(
        source=paths,
        imgsz=960,
        conf=0.05,            # 문턱값 확 낮춰서 애매하게 놓친 것들 재시도
        save=True,             # 박스 그려진 이미지 저장 (육안 검수용, 여기 결과는 꼭 확인 필요)
        save_txt=True,
        save_conf=True,        # 확신도도 같이 기록 (검수 시 낮은 확신도는 더 의심)
        project=src_dir,
        name="recheck_labels",
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

    print(f"\n83장 중 여전히 완전 미검출: {len(still_missing)}장")
    if still_missing:
        list_path = os.path.join(src_dir, "still_no_detection.txt")
        with open(list_path, "w", encoding="utf-8") as f:
            for n in still_missing:
                f.write(n + "\n")
        print("목록 저장:", list_path)
