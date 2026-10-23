import sys

sys.argv = [
    'train.py',
    '--exp_name', 'TPS-ResNet-BiLSTM-CTC-v8c',
    '--train_data', 'C:/plate_dataset/lmdb/train_v7',
    '--valid_data', 'C:/plate_dataset/lmdb/val_v6',
    '--select_data', '/',
    '--batch_ratio', '1.0',
    '--workers', '0',
    '--saved_model', 'saved_models/TPS-ResNet-BiLSTM-CTC-v6/best_accuracy.pth',
    '--Transformation', 'TPS',
    '--FeatureExtraction', 'ResNet',
    '--SequenceModeling', 'BiLSTM',
    '--Prediction', 'CTC',
    '--character', '0123456789가나다라마바사아자거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배충육기북합남울강원경산전대국해광천종세인제',
    '--imgH', '32',
    '--imgW', '100',
    '--batch_size', '64',
    '--num_iter', '5000',
    '--valInterval', '500',
]

import runpy
runpy.run_path(r'C:\plate_dataset\deep-text-recognition-benchmark\train.py', run_name='__main__')