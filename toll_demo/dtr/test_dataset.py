from dataset import LmdbDataset
from PIL import Image


# opt 옵션 대용 클래스 (필요한 설정값만 임시 지정)
class Option:

    def __init__(self):
        self.data_filtering_off = True
        self.batch_max_length = 25
        self.character = "0123456789가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호바사아자차카타파하서울경기인천강원충북충남전북전남경북경남제주세종대구부산울산광주"
        self.rgb = False  # Grayscale 사용
        self.sensitive = True


# 1. Option 객체 생성 및 LMDB 데이터셋 불러오기
opt = Option()
# 'data_lmdb/train' 경로를 실제 LMDB 폴더 경로로 맞춰주세요.
dataset = LmdbDataset(root="C:/plate_dataset/lmdb/train_v2", opt=opt)

# 2. 첫 번째 데이터(0번 인덱스) 꺼내기
img, label = dataset[0]

# 3. 데이터 결과 출력 및 전처리된 이미지 파일로 저장
print(f"불러온 데이터 라벨: {label}")
print(f"이미지 크기 및 모드: {img.size}, {img.mode}")

# 이미지를 저장해서 전처리가 잘 되었는지 확인!
img.save("check_preprocessed_sample.png")
print("전처리된 이미지가 'check_preprocessed_sample.png'로 저장되었습니다.")