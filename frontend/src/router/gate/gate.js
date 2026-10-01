// 번호판 인식(CargoScan) 게이트 통과 데모.
// 26.10.01 변경: 게이트 OCR 검사 화면을 관리자 메뉴(/admin/gate-ocr)로 옮김.
// 예전 주소(/gate-demo)로 들어와도 새 주소로 이동하도록 리다이렉트만 남겨둔다.
export default [
  {
    path: "/gate-demo",
    name: "gate-demo",
    redirect: "/admin/gate-ocr",
  },
];
