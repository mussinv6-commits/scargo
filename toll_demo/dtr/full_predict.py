import sys
sys.path.append(r'C:\plate_dataset\deep-text-recognition-benchmark')

import torch
import torch.nn.functional as F
from torch.utils.data import DataLoader
from model import Model
from utils import CTCLabelConverter
from dataset import hierarchical_dataset, AlignCollate

class Opt:
    Transformation = 'TPS'
    FeatureExtraction = 'ResNet'
    SequenceModeling = 'BiLSTM'
    Prediction = 'CTC'
    character = "0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해광천종세인제"
    imgH = 32
    imgW = 100
    batch_max_length = 25
    num_fiducial = 20
    input_channel = 1
    output_channel = 512
    hidden_size = 256
    rgb = False
    num_gpu = 1
    PAD = False
    data_filtering_off = False
    sensitive = False
    workers = 0
    batch_size = 64
    train_data = 'C:/plate_dataset/lmdb/val_v6'  # 실제 경로로 수정

opt = Opt()
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
converter = CTCLabelConverter(opt.character)
opt.num_class = len(converter.character)

model = Model(opt)
model = torch.nn.DataParallel(model).to(device)
model.load_state_dict(torch.load(
    r'C:\plate_dataset\deep-text-recognition-benchmark\saved_models\TPS-ResNet-BiLSTM-CTC-v6\best_accuracy.pth',
    map_location=device
))
model.eval()

dataset, _ = hierarchical_dataset(root=opt.train_data, opt=opt, select_data=['/'])
align_collate = AlignCollate(imgH=opt.imgH, imgW=opt.imgW, keep_ratio_with_pad=opt.PAD)
loader = DataLoader(dataset, batch_size=opt.batch_size, shuffle=False, num_workers=0, collate_fn=align_collate)

results = []
with torch.no_grad():
    for images, labels in loader:
        images = images.to(device)
        batch_size = images.size(0)
        text_for_pred = torch.LongTensor(batch_size, opt.batch_max_length + 1).fill_(0).to(device)
        preds = model(images, text_for_pred)
        preds_size = torch.IntTensor([preds.size(1)] * batch_size)
        _, preds_index = preds.max(2)
        preds_str = converter.decode(preds_index, preds_size)
        for gt, pred in zip(labels, preds_str):
            results.append((gt, pred, gt == pred))

with open('full_predictions_v6_val.txt', 'w', encoding='utf-8') as f:
    for gt, pred, is_correct in results:
        f.write(f"{gt}\t{pred}\t{is_correct}\n")

correct_count = sum(1 for _, _, c in results if c)
print(f"전체: {len(results)}개, 정답: {correct_count}개 ({correct_count/len(results)*100:.2f}%)")