import os
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

from ultralytics import YOLO

if __name__ == "__main__":  # Windows에서 필수
    model = YOLO("yolo11l.pt")  # run-16(yolo11m): mAP50-95=0.874 (직전 최고) -> 다음 실험: 모델 한 단계 더 확대 (yolo11m 효과 있었으니 yolo11l도 시도)

    model.train(
        data=r"C:\Users\user\Desktop\project.v4i.yolov8\data.yaml",
        epochs=100,          # 100 epoch 기준으로 변경 (run-17: patience=30으로 109에서 조기종료, mAP는 run-16보다 소폭 하락)
        patience=30,        # 30 epoch 개선 없으면 조기 종료
        imgsz=960,          # run-14/16과 동일 유지 (해상도 실험은 이미 기각됨) -> 모델 크기만 변경해서 비교
        batch=4,             # yolo11l은 yolo11m보다 더 무거워서 batch를 6->4로 보수적으로 낮춤 (VRAM 8GB, autobatch는 오작동 이력 있어 명시)
        box=15.0,           # 박스 위치 손실 가중치 (기본 7.5) -> 10에서 더 강화
        cos_lr=True,        # 학습률 부드럽게 감소
        close_mosaic=20,    # 마지막 20 epoch은 mosaic 끄고 정밀 학습
        optimizer="AdamW",
        lr0=0.001,
        workers=4,
        cache=True,          # 이미지를 RAM에 캐싱 -> 2번째 epoch부터 속도 크게 개선 (960 해상도에서는 문제 없음)
        name="plate_detect_l",
        device=0,           # GPU 없으면 "cpu"
    )
