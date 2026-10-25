-- 26.10.24 점검표 손상 사진 첨부 (설치 SQL 을 이미 넣은 DB 에 1회 실행, 다시 실행해도 안전)
-- 실행: psql -U postgres -d scargo_demo -f 추가_20261024_기능4종.sql
-- (하역 완료 · 일일 보고서 · 내보내기는 DB 변경 없음)

CREATE TABLE IF NOT EXISTS public.container_inspection_photos (
    photo_id bigserial PRIMARY KEY,
    inspection_id bigint NOT NULL REFERENCES public.container_inspections(inspection_id) ON DELETE RESTRICT,
    file_name varchar(200) NOT NULL,
    stored_name varchar(100) NOT NULL,
    content_type varchar(50) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    uploaded_by_account_id bigint,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_photos_inspection ON public.container_inspection_photos (inspection_id);

COMMENT ON TABLE public.container_inspection_photos IS '컨테이너 점검표 손상 사진 (파일은 서버 uploads/inspections/{id}/ 에 저장)';
