import io
import sys

# 콘솔 인코딩 문제 방지 - 출력을 항상 UTF-8로 강제
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 명령줄 인자를 코드 안에서 직접 설정 (cmd를 거치지 않아 인코딩 깨질 일 없음)
sys.argv = [
    'test.py',
    '--eval_data', 'C:/plate_dataset/lmdb/val_v2',
    '--saved_model', 'saved_models/TPS-ResNet-BiLSTM-CTC-v2-full/best_accuracy.pth',
    '--Transformation', 'TPS',
    '--FeatureExtraction', 'ResNet',
    '--SequenceModeling', 'BiLSTM',
    '--Prediction', 'CTC',
    '--character', '0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해',
    '--imgH', '32',
    '--imgW', '100',
    '--batch_size', '64',
    '--workers', '0',
]

import runpy
runpy.run_path(
    r'C:\plate_dataset\deep-text-recognition-benchmark\test.py',
    run_name='__main__'
)