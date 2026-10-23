import os
import sys
import re
import six
import math
import random
import lmdb
import torch
import cv2  # OpenCV 추가

from natsort import natsorted
from PIL import Image, ImageEnhance, ImageFilter
import numpy as np
from torch.utils.data import Dataset, ConcatDataset, Subset
from itertools import accumulate as _accumulate
import torchvision.transforms as transforms


# =====================================================================
# [OCR 전처리 함수 정의]
# CLAHE(대비 강조), Blur(노이즈 제거), Adaptive Thresholding(이진화) 적용
# =====================================================================
def preprocess_license_plate_opencv(pil_img):
    return pil_img  # 임시로 전처리 비활성화, 원본 그대로 반환
    img_np = np.array(pil_img)

    # 차원 확인 및 Grayscale 변환
    if len(img_np.shape) == 3:
        if img_np.shape[2] == 4:
            img_np = cv2.cvtColor(img_np, cv2.COLOR_RGBA2BGR)
        else:
            img_np = cv2.cvtColor(img_np, cv2.COLOR_RGB2BGR)
        gray = cv2.cvtColor(img_np, cv2.COLOR_BGR2GRAY)
    else:
        gray = img_np


    # 1. CLAHE (대비 강화 - 지역명/희미한 글자 선명화)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)

    # 2. Gaussian Blur (미세 노이즈 제거)
    blurred = cv2.GaussianBlur(enhanced, (3, 3), 0)

    # 3. Adaptive Thresholding (이진화 - 배경과 글자 명확 분리)
    binary = cv2.adaptiveThreshold(
        blurred,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        11,
        2,
    )

    # 5. [추가] 모폴로지 열기 연산 (자잘한 점 노이즈 제거)
    kernel = np.ones((2, 2), np.uint8)
    binary = cv2.morphologyEx(binary, cv2.MORPH_OPEN, kernel)

    # PIL Image로 다시 변환하여 기존 Transform 파이프라인 전달
    return Image.fromarray(binary)


# =====================================================================
# [학습 전용 증강] train_data에만 적용되고 valid_data/test/추론에는 절대 적용되지 않는다.
# (LmdbDataset/hierarchical_dataset에 is_train=True를 명시적으로 넘긴 경우에만 동작한다.
#  기존 코드(train.py의 valid_data 로딩, full_predict.py, eval_padding.py 등)는 is_train을
#  넘기지 않으므로 기본값 False가 적용되어 지금까지와 똑같이 원본 그대로 동작한다.)
#
# 넣은 근거
#   - 크롭 여백(경계) 흔들기: YOLO 박스 비교 실험에서 박스를 3~6% 느슨하게 잘랐을 때 정확도가
#     가장 높았고, 좁게 잘랐을 때는 크게 떨어졌다. 그래서 여백을 늘리는 쪽을 자주, 좁히는 쪽은
#     드물고 약하게만 넣는다.
#   - 밝기/대비/감마: 오답 분석에서 밝기·대비가 낮은 크롭에서 오답이 뚜렷하게 많았다.
#   - 해상도 저하(축소 후 확대)/블러/노이즈: 저해상도(Roboflow 512px) 그룹에서 오류가 몰렸던 것에 대응.
# 각 효과는 배치마다 독립적인 확률로 적용되고, 아무 효과도 안 걸려 원본 그대로인 경우도 남겨둔다.
# =====================================================================
AUG_ENABLED = False      # False면 augment_plate_train이 항상 원본을 그대로 돌려준다 (is_train 여부와 무관).
                         # train_v7 데이터 자체의 효과만 보려는 분리 실험(증강 없이 학습)을 위한 스위치.
                         # 증강을 다시 켤 때는 이 값을 True로만 바꾸면 되고, 나머지 코드는 그대로 둔다.

AUG_P_MARGIN = 0.35      # 크롭 여백 흔들기 (기하학적 변화라 다른 효과와 함께 걸려도 됨)
AUG_P_QUALITY = 0.35     # 밝기/대비/감마 중 "하나만" 골라 적용할 확률 (동시에 여러 개 안 걸리게 그룹으로 묶음)
AUG_P_RESOLUTION_GROUP = 0.25  # 해상도저하/블러 중 "하나만" 골라 적용할 확률
AUG_P_NOISE = 0.20       # 노이즈는 약하게, 독립적으로

AUG_LOOSEN_PROB = 0.85   # 여백 흔들기 중 "느슨하게"를 고를 확률 (나머지는 "좁게")
AUG_LOOSEN_MAX = 0.08    # 느슨하게: 가장자리를 최대 이 비율만큼 늘림(가장자리 픽셀 복제)
AUG_TIGHTEN_MAX = 0.02   # 좁게: 가장자리를 최대 이 비율만큼 잘라냄 (이전보다 더 약하게)

def _margin_jitter(img):
    """크롭 가장자리에 여백을 늘리거나(대개) 살짝 좁힌다. 최종 크기는 이후 ResizeNormalize/NormalizePAD가
    imgH x imgW로 다시 맞추므로, 여기서 가로세로 비율이 달라져도 상관없다."""
    arr = np.array(img)
    h, w = arr.shape[0], arr.shape[1]
    if h < 4 or w < 4:
        return img
    if random.random() < AUG_LOOSEN_PROB:
        ph = int(round(w * random.uniform(0, AUG_LOOSEN_MAX)))
        pv = int(round(h * random.uniform(0, AUG_LOOSEN_MAX)))
        if ph == 0 and pv == 0:
            return img
        pad_width = ((pv, pv), (ph, ph), (0, 0)) if arr.ndim == 3 else ((pv, pv), (ph, ph))
        arr = np.pad(arr, pad_width, mode='edge')
    else:
        ph = min(int(round(w * random.uniform(0, AUG_TIGHTEN_MAX))), max(0, (w - 4) // 2))
        pv = min(int(round(h * random.uniform(0, AUG_TIGHTEN_MAX))), max(0, (h - 4) // 2))
        if ph == 0 and pv == 0:
            return img
        arr = arr[pv:h - pv if pv else h, ph:w - ph if ph else w]
    return Image.fromarray(arr.astype(np.uint8), mode=img.mode)


def _add_noise(img):
    arr = np.array(img).astype(np.float32)
    noise = np.random.normal(0, random.uniform(4, 12), arr.shape)
    arr = np.clip(arr + noise, 0, 255)
    return Image.fromarray(arr.astype(np.uint8), mode=img.mode)


def _gamma_darken(img):
    arr = np.array(img).astype(np.float32) / 255.0
    arr = np.power(arr, random.uniform(1.1, 1.8)) * 255.0
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), mode=img.mode)


def _resolution_drop(img):
    w, h = img.size
    scale = random.uniform(0.5, 0.85)
    small = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.BILINEAR)
    return small.resize((w, h), Image.BILINEAR)


def augment_plate_train(img):
    """PIL Image(L 또는 RGB)를 받아 학습용으로 무작위 증강한 PIL Image를 반환한다. 학습에만 쓴다.
    같은 계열(밝기 관련 / 해상도 관련) 효과는 한 번에 하나만 걸리도록 묶어서, 여러 왜곡이 동시에
    겹쳐 실전에는 없는 극단적인 이미지가 되는 것을 피한다. (v8 학습에서 여러 효과가 동시에 겹쳐
    정확도가 v6보다 낮게 나온 문제를 겪은 뒤 이렇게 조정했다.)"""
    if not AUG_ENABLED:
        return img  # 증강 끔: train_v7 데이터 자체만의 효과를 보기 위한 분리 실험용

    if random.random() < AUG_P_MARGIN:
        img = _margin_jitter(img)

    if random.random() < AUG_P_QUALITY:
        choice = random.choice(['brightness', 'contrast', 'gamma'])
        if choice == 'brightness':
            img = ImageEnhance.Brightness(img).enhance(random.uniform(0.6, 1.3))
        elif choice == 'contrast':
            img = ImageEnhance.Contrast(img).enhance(random.uniform(0.7, 1.2))
        else:
            img = _gamma_darken(img)

    if random.random() < AUG_P_RESOLUTION_GROUP:
        if random.random() < 0.5:
            img = _resolution_drop(img)
        else:
            img = img.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.3, 0.7)))

    if random.random() < AUG_P_NOISE:
        img = _add_noise(img)

    return img


class Batch_Balanced_Dataset(object):

    def __init__(self, opt):
        """
        Modulate the data ratio in the batch.
        For example, when select_data is "MJ-ST" and batch_ratio is "0.5-0.5",
        the 50% of the batch is filled with MJ and the other 50% of the batch is filled with ST.
        """
        log = open(f'./saved_models/{opt.exp_name}/log_dataset.txt', 'a')
        dashed_line = '-' * 80
        print(dashed_line)
        log.write(dashed_line + '\n')
        print(f'dataset_root: {opt.train_data}\nopt.select_data: {opt.select_data}\nopt.batch_ratio: {opt.batch_ratio}')
        log.write(f'dataset_root: {opt.train_data}\nopt.select_data: {opt.select_data}\nopt.batch_ratio: {opt.batch_ratio}\n')
        assert len(opt.select_data) == len(opt.batch_ratio)

        _AlignCollate = AlignCollate(imgH=opt.imgH, imgW=opt.imgW, keep_ratio_with_pad=opt.PAD)
        self.data_loader_list = []
        self.dataloader_iter_list = []
        batch_size_list = []
        Total_batch_size = 0
        for selected_d, batch_ratio_d in zip(opt.select_data, opt.batch_ratio):
            _batch_size = max(round(opt.batch_size * float(batch_ratio_d)), 1)
            print(dashed_line)
            log.write(dashed_line + '\n')
            # is_train=True: 학습 데이터에만 증강을 적용한다 (valid_data 로딩 쪽은 이 인자를 넘기지 않아 기본값 False)
            _dataset, _dataset_log = hierarchical_dataset(root=opt.train_data, opt=opt, select_data=[selected_d], is_train=True)
            total_number_dataset = len(_dataset)
            log.write(_dataset_log)

            """
            The total number of data can be modified with opt.total_data_usage_ratio.
            ex) opt.total_data_usage_ratio = 1 indicates 100% usage, and 0.2 indicates 20% usage.
            See 4.2 section in our paper.
            """
            number_dataset = int(total_number_dataset * float(opt.total_data_usage_ratio))
            dataset_split = [number_dataset, total_number_dataset - number_dataset]
            indices = range(total_number_dataset)
            _dataset, _ = [Subset(_dataset, indices[offset - length:offset])
                           for offset, length in zip(_accumulate(dataset_split), dataset_split)]
            selected_d_log = f'num total samples of {selected_d}: {total_number_dataset} x {opt.total_data_usage_ratio} (total_data_usage_ratio) = {len(_dataset)}\n'
            selected_d_log += f'num samples of {selected_d} per batch: {opt.batch_size} x {float(batch_ratio_d)} (batch_ratio) = {_batch_size}'
            print(selected_d_log)
            log.write(selected_d_log + '\n')
            batch_size_list.append(str(_batch_size))
            Total_batch_size += _batch_size

            _data_loader = torch.utils.data.DataLoader(
                _dataset, batch_size=_batch_size,
                shuffle=True,
                num_workers=int(opt.workers),
                collate_fn=_AlignCollate, pin_memory=True)
            self.data_loader_list.append(_data_loader)
            self.dataloader_iter_list.append(iter(_data_loader))

        Total_batch_size_log = f'{dashed_line}\n'
        batch_size_sum = '+'.join(batch_size_list)
        Total_batch_size_log += f'Total_batch_size: {batch_size_sum} = {Total_batch_size}\n'
        Total_batch_size_log += f'{dashed_line}'
        opt.batch_size = Total_batch_size

        print(Total_batch_size_log)
        log.write(Total_batch_size_log + '\n')
        log.close()

    def get_batch(self):
        balanced_batch_images = []
        balanced_batch_texts = []

        for i, data_loader_iter in enumerate(self.dataloader_iter_list):
            try:
                image, text = next(data_loader_iter)
                balanced_batch_images.append(image)
                balanced_batch_texts += text
            except StopIteration:
                self.dataloader_iter_list[i] = iter(self.data_loader_list[i])
                image, text = next(self.dataloader_iter_list[i])
                balanced_batch_images.append(image)
                balanced_batch_texts += text
            except ValueError:
                pass

        balanced_batch_images = torch.cat(balanced_batch_images, 0)

        return balanced_batch_images, balanced_batch_texts


def hierarchical_dataset(root, opt, select_data='/', is_train=False):
    """ select_data='/' contains all sub-directory of root directory
    is_train=True 인 경우에만 그 안의 LmdbDataset이 학습용 증강(augment_plate_train)을 적용한다.
    기본값은 False라서, 이 인자를 넘기지 않는 기존 호출(검증/테스트/추론)은 이전과 똑같이 동작한다."""
    dataset_list = []
    dataset_log = f'dataset_root:    {root}\t dataset: {select_data[0]}'
    print(dataset_log)
    dataset_log += '\n'
    for dirpath, dirnames, filenames in os.walk(root+'/'):
        if not dirnames:
            select_flag = False
            for selected_d in select_data:
                if selected_d in dirpath:
                    select_flag = True
                    break

            if select_flag:
                dataset = LmdbDataset(dirpath, opt, is_train=is_train)
                sub_dataset_log = f'sub-directory:\t/{os.path.relpath(dirpath, root)}\t num samples: {len(dataset)}'
                print(sub_dataset_log)
                dataset_log += f'{sub_dataset_log}\n'
                dataset_list.append(dataset)

    concatenated_dataset = ConcatDataset(dataset_list)

    return concatenated_dataset, dataset_log


class LmdbDataset(Dataset):

    def __init__(self, root, opt, is_train=False):

        self.root = root
        self.opt = opt
        self.is_train = is_train  # True일 때만 __getitem__에서 augment_plate_train을 적용한다
        self.env = lmdb.open(root, max_readers=32, readonly=True, lock=False, readahead=False, meminit=False)
        if not self.env:
            print('cannot create lmdb from %s' % (root))
            sys.exit(0)

        with self.env.begin(write=False) as txn:
            nSamples = int(txn.get('num-samples'.encode()))
            self.nSamples = nSamples

            if self.opt.data_filtering_off:
                self.filtered_index_list = [index + 1 for index in range(self.nSamples)]
            else:
                self.filtered_index_list = []
                for index in range(self.nSamples):
                    index += 1  # lmdb starts with 1
                    label_key = 'label-%09d'.encode() % index
                    label = txn.get(label_key).decode('utf-8')

                    if len(label) > self.opt.batch_max_length:
                        continue

                    out_of_char = f'[^{self.opt.character}]'
                    if re.search(out_of_char, label.lower()):
                        continue

                    self.filtered_index_list.append(index)

                self.nSamples = len(self.filtered_index_list)

    def __len__(self):
        return self.nSamples

    def __getitem__(self, index):
        assert index <= len(self), 'index range error'
        index = self.filtered_index_list[index]

        with self.env.begin(write=False) as txn:
            label_key = 'label-%09d'.encode() % index
            label = txn.get(label_key).decode('utf-8')
            img_key = 'image-%09d'.encode() % index
            imgbuf = txn.get(img_key)

            buf = six.BytesIO()
            buf.write(imgbuf)
            buf.seek(0)
            try:
                if self.opt.rgb:
                    img = Image.open(buf).convert('RGB')
                else:
                    img = Image.open(buf).convert('L')

                # =========================================================
                # [전처리 적용] 이미지 로드 직후 OpenCV 전처리 함수 통과
                # =========================================================
                img = preprocess_license_plate_opencv(img)

                # =========================================================
                # [학습 전용 증강] 검증/테스트/추론(is_train=False)에는 적용되지 않는다
                # =========================================================
                if self.is_train:
                    img = augment_plate_train(img)

            except IOError:
                print(f'Corrupted image for {index}')
                if self.opt.rgb:
                    img = Image.new('RGB', (self.opt.imgW, self.opt.imgH))
                else:
                    img = Image.new('L', (self.opt.imgW, self.opt.imgH))
                label = '[dummy_label]'

            if not self.opt.sensitive:
                label = label.lower()

            out_of_char = f'[^{self.opt.character}]'
            label = re.sub(out_of_char, '', label)

        return (img, label)


class RawDataset(Dataset):

    def __init__(self, root, opt):
        self.opt = opt
        self.image_path_list = []
        for dirpath, dirnames, filenames in os.walk(root):
            for name in filenames:
                _, ext = os.path.splitext(name)
                ext = ext.lower()
                if ext == '.jpg' or ext == '.jpeg' or ext == '.png':
                    self.image_path_list.append(os.path.join(dirpath, name))

        self.image_path_list = natsorted(self.image_path_list)
        self.nSamples = len(self.image_path_list)

    def __len__(self):
        return self.nSamples

    def __getitem__(self, index):

        try:
            if self.opt.rgb:
                img = Image.open(self.image_path_list[index]).convert('RGB')
            else:
                img = Image.open(self.image_path_list[index]).convert('L')

            # =========================================================
            # [전처리 적용] Raw 이미지 로드 직후에도 전처리 적용 (증강은 적용하지 않음: 추론용이기 때문)
            # =========================================================
            img = preprocess_license_plate_opencv(img)

        except IOError:
            print(f'Corrupted image for {index}')
            if self.opt.rgb:
                img = Image.new('RGB', (self.opt.imgW, self.opt.imgH))
            else:
                img = Image.new('L', (self.opt.imgW, self.opt.imgH))

        return (img, self.image_path_list[index])


class ResizeNormalize(object):

    def __init__(self, size, interpolation=Image.BICUBIC):
        self.size = size
        self.interpolation = interpolation
        self.toTensor = transforms.ToTensor()

    def __call__(self, img):
        img = img.resize(self.size, self.interpolation)
        img = self.toTensor(img)
        img.sub_(0.5).div_(0.5)
        return img


class NormalizePAD(object):

    def __init__(self, max_size, PAD_type='right'):
        self.toTensor = transforms.ToTensor()
        self.max_size = max_size
        self.max_width_half = math.floor(max_size[2] / 2)
        self.PAD_type = PAD_type

    def __call__(self, img):
        img = self.toTensor(img)
        img.sub_(0.5).div_(0.5)
        c, h, w = img.size()
        Pad_img = torch.FloatTensor(*self.max_size).fill_(0)
        Pad_img[:, :, :w] = img
        if self.max_size[2] != w:
            Pad_img[:, :, w:] = img[:, :, w - 1].unsqueeze(2).expand(c, h, self.max_size[2] - w)

        return Pad_img


class AlignCollate(object):

    def __init__(self, imgH=32, imgW=100, keep_ratio_with_pad=False):
        self.imgH = imgH
        self.imgW = imgW
        self.keep_ratio_with_pad = keep_ratio_with_pad

    def __call__(self, batch):
        batch = filter(lambda x: x is not None, batch)
        images, labels = zip(*batch)

        if self.keep_ratio_with_pad:
            resized_max_w = self.imgW
            input_channel = 3 if images[0].mode == 'RGB' else 1
            transform = NormalizePAD((input_channel, self.imgH, resized_max_w))

            resized_images = []
            for image in images:
                w, h = image.size
                ratio = w / float(h)
                if math.ceil(self.imgH * ratio) > self.imgW:
                    resized_w = self.imgW
                else:
                    resized_w = math.ceil(self.imgH * ratio)

                resized_image = image.resize((resized_w, self.imgH), Image.BICUBIC)
                resized_images.append(transform(resized_image))

            image_tensors = torch.cat([t.unsqueeze(0) for t in resized_images], 0)

        else:
            transform = ResizeNormalize((self.imgW, self.imgH))
            image_tensors = [transform(image) for image in images]
            image_tensors = torch.cat([t.unsqueeze(0) for t in image_tensors], 0)

        return image_tensors, labels


def tensor2im(image_tensor, imtype=np.uint8):
    image_numpy = image_tensor.cpu().float().numpy()
    if image_numpy.shape[0] == 1:
        image_numpy = np.tile(image_numpy, (3, 1, 1))
    image_numpy = (np.transpose(image_numpy, (1, 2, 0)) + 1) / 2.0 * 255.0
    return image_numpy.astype(imtype)


def save_image(image_numpy, image_path):
    image_pil = Image.fromarray(image_numpy)
    image_pil.save(image_path)