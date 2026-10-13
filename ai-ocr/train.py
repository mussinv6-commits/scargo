from ultralytics import YOLO


def main():
    model = YOLO("yolov8n.pt")
    model.train(
        data="data.yaml",
        epochs=50,
        imgsz=640,
        batch=8,
        patience=15,
        project="runs",
        name="plate_detect",
    )
    print("Training done -> runs/plate_detect/weights/best.pt")


if __name__ == "__main__":
    main()