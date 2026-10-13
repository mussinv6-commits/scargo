from ultralytics import YOLO

model = YOLO(r"C:\Users\user\Desktop\project.v4i.yolov8\runs\detect\plate_detect\weights\best.pt")

# 테스트할 이미지 경로로 바꿔서 사용 (폴더 경로도 가능)
results = model.predict(
    source=r"C:\Users\user\Desktop\test.jpg",
    conf=0.25,
    save=True,
)

for r in results:
    print(r.boxes.xyxy, r.boxes.conf)
