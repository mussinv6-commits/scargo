-- add_5_teammate.sql : 기존 DB(add_1~4 적용 후)에 팀원 2차 기능(봉인·지선·반납·승인 등)을 추가한다.
-- 기존 테이블·데이터는 지우지 않고 없는 것만 추가한다. 한 번만 실행 (setup_db.bat 이 자동 실행).

-- ===== 기존 테이블에 컬럼 추가 =====
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS approval_status character varying(20) DEFAULT 'APPROVED'::character varying NOT NULL;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS approved_at timestamp with time zone;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS approved_by_account_id bigint;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS reject_reason text;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS license_no character varying(30);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS is_carrier boolean DEFAULT false NOT NULL;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS approval_status character varying(20);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS approved_at timestamp with time zone;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS approved_by_account_id bigint;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS reject_reason text;
ALTER TABLE container_seals ADD COLUMN IF NOT EXISTS open_hold boolean DEFAULT false NOT NULL;
ALTER TABLE container_seals ADD COLUMN IF NOT EXISTS reclass_required boolean DEFAULT false NOT NULL;
ALTER TABLE hub_vehicles ADD COLUMN IF NOT EXISTS company_id bigint;
ALTER TABLE hub_vehicles ADD COLUMN IF NOT EXISTS vehicle_no character varying(20);
ALTER TABLE parcels ADD COLUMN IF NOT EXISTS unit_id character varying(30);
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS driver_account_id bigint;
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS domestic_seal_no character varying(30);
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS planned_units integer;
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS planned_depart_at timestamp with time zone;
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS hub_arrived_at timestamp with time zone;
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS inspected_at timestamp with time zone;
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS arrival_seal_no character varying(30);
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS arrival_seal_checked_at timestamp with time zone;
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS arrival_seal_checked_by character varying(100);
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_status character varying(20);
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_cy text;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_due_at timestamp with time zone;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_started_at timestamp with time zone;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_arrived_at timestamp with time zone;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS empty_returned_at timestamp with time zone;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_photo_url text;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS shipper_name character varying(100);
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_cy_id bigint;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_receiver_name character varying(50);
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_eir_no character varying(30);
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_ready_at timestamp with time zone;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_driver_account_id bigint;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_instructed_at timestamp with time zone;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS return_instructed_by_account_id bigint;
ALTER TABLE transport_orders ADD COLUMN IF NOT EXISTS damage_noted boolean DEFAULT false NOT NULL;

-- ===== 함수 =====
CREATE OR REPLACE FUNCTION scargo_validate_carrier_contact()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.user_type = 'CORPORATE_APPROVED' AND NOT EXISTS
     (SELECT 1 FROM companies WHERE company_id = NEW.company_id AND is_carrier) THEN
    RAISE EXCEPTION '운송사 계정(CORPORATE_APPROVED)은 운송사(is_carrier) 소속이어야 합니다 (user_id=%)', NEW.user_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_guard_carrier_company()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF OLD.is_carrier AND NOT NEW.is_carrier AND EXISTS
     (SELECT 1 FROM accounts WHERE company_id = NEW.company_id AND user_type = 'CORPORATE_APPROVED') THEN
    RAISE EXCEPTION '운송사 계정이 소속된 회사는 운송사 지정을 해제할 수 없습니다 (company_id=%)', NEW.company_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_branch_unit()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.assignment_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM shipment_legs
       WHERE assignment_id = NEW.assignment_id AND leg_type = 'HUB_TO_CENTER') THEN
    RAISE EXCEPTION '롤테이너는 허브→센터 구간에만 연결 가능 (assignment_id=%)', NEW.assignment_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_company_approver()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.approved_by_account_id IS NOT NULL AND NOT EXISTS
     (SELECT 1 FROM accounts WHERE account_id = NEW.approved_by_account_id AND user_type = 'ADMIN') THEN
    RAISE EXCEPTION '운송사 승인자는 ADMIN 계정이어야 합니다 (account_id=%)', NEW.approved_by_account_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_notify_overload_block()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE cid BIGINT;
BEGIN
  IF NEW.passage_status = 'BLOCKED' AND NEW.block_reason = 'OVERLOAD'
     AND (TG_OP = 'INSERT' OR OLD.passage_status IS DISTINCT FROM 'BLOCKED') THEN
    SELECT t.company_id INTO cid FROM trucks t JOIN companies c ON c.company_id = t.company_id
     WHERE t.vehicle_no = NEW.vehicle_no AND c.is_carrier;
    IF cid IS NOT NULL THEN
      INSERT INTO notifications (company_id, title, message, notification_type, reference_id)
      VALUES (cid, '과적 운행 금지', '차량 ' || NEW.vehicle_no || ' 톨게이트 과적 차단', 'OVERLOAD_WARNING', NEW.passage_id);
    END IF;
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_notify_seal_lost()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE cid BIGINT;
BEGIN
  IF NEW.exception_type <> 'SEAL_LOST' THEN RETURN NEW; END IF;
  INSERT INTO notifications (account_id, title, message, notification_type, reference_id)
  SELECT a.account_id, '씰 분실 확정', COALESCE(NEW.message, '씰 분실'), 'EXCEPTION', NEW.exception_id
    FROM accounts a WHERE a.user_type = 'CONTROL';
  IF NEW.source_table = 'seal_events' THEN
    SELECT o.company_id INTO cid FROM seal_events se
      JOIN transport_orders o ON o.shipment_id = se.shipment_id
      JOIN companies c ON c.company_id = o.company_id AND c.is_carrier
     WHERE se.event_id = NEW.source_key::bigint
     ORDER BY o.created_at DESC LIMIT 1;
  END IF;
  IF cid IS NOT NULL THEN
    INSERT INTO notifications (company_id, title, message, notification_type, reference_id)
    VALUES (cid, '씰 분실 확정', COALESCE(NEW.message, '씰 분실'), 'EXCEPTION', NEW.exception_id);
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_account_approver()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.approved_by_account_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM accounts a LEFT JOIN companies c ON c.company_id = a.company_id
        WHERE a.account_id = NEW.approved_by_account_id
          AND (a.user_type = 'ADMIN'
               OR (a.user_type = 'CORPORATE_APPROVED' AND a.company_id = NEW.company_id
                   AND c.is_carrier AND c.approval_status = 'APPROVED'))) THEN
    RAISE EXCEPTION '기사 승인자는 ADMIN 또는 승인된 같은 회사 운송사 계정이어야 합니다 (account_id=%)', NEW.approved_by_account_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_return_start()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.return_status IS NOT NULL AND (TG_OP = 'INSERT' OR OLD.return_status IS NULL) THEN
    IF NEW.return_status <> 'RETURNING_EMPTY' THEN
      RAISE EXCEPTION '공컨 반납은 RETURNING_EMPTY 부터 시작합니다' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM hub_inbound_receipts r
                    WHERE r.assignment_id = NEW.assignment_id AND r.status = 'COMPLETED' AND r.unloaded_at IS NOT NULL) THEN
      RAISE EXCEPTION '허브 하역 완료 전에는 공컨 반납을 시작할 수 없습니다 (order_id=%)', NEW.order_id USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM empty_container_inspections i WHERE i.order_id = NEW.order_id) THEN
      RAISE EXCEPTION '공컨 상태 점검 전에는 공컨 반납을 시작할 수 없습니다 (order_id=%)', NEW.order_id USING ERRCODE = '23514';
    END IF;
    IF NEW.return_ready_at IS NULL OR NEW.return_driver_account_id IS NULL OR NEW.return_instructed_at IS NULL THEN
      RAISE EXCEPTION '반납 지시(기사 지정) 전에는 공컨 반납을 시작할 수 없습니다 (order_id=%)', NEW.order_id USING ERRCODE = '23514';
    END IF;
  ELSIF TG_OP = 'UPDATE' AND NEW.return_status IS DISTINCT FROM OLD.return_status THEN
    IF NOT ((OLD.return_status = 'RETURNING_EMPTY' AND NEW.return_status = 'RETURN_ARRIVED')
         OR (OLD.return_status = 'RETURN_ARRIVED' AND NEW.return_status = 'EMPTY_RETURNED')) THEN
      RAISE EXCEPTION '공컨 반납 단계는 RETURNING_EMPTY → RETURN_ARRIVED → EMPTY_RETURNED 순서만 가능합니다 (% → %)',
        OLD.return_status, COALESCE(NEW.return_status,'NULL') USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_return_roles()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.return_driver_account_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM accounts a WHERE a.account_id = NEW.return_driver_account_id
         AND a.user_type = 'CONTAINER_DRIVER' AND a.company_id = NEW.company_id) THEN
    RAISE EXCEPTION '반납 기사는 오더 운송사 소속 CONTAINER_DRIVER 여야 합니다 (account_id=%)', NEW.return_driver_account_id USING ERRCODE = '23514';
  END IF;
  IF NEW.return_instructed_by_account_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM accounts a WHERE a.account_id = NEW.return_instructed_by_account_id
         AND (a.user_type = 'ADMIN' OR (a.user_type = 'CORPORATE_APPROVED' AND a.company_id = NEW.company_id))) THEN
    RAISE EXCEPTION '반납 지시자는 ADMIN 또는 오더 운송사 계정이어야 합니다 (account_id=%)', NEW.return_instructed_by_account_id USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_empty_inspection()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM transport_orders o WHERE o.order_id = NEW.order_id AND o.container_no = NEW.container_no) THEN
    RAISE EXCEPTION '공컨 점검 컨테이너가 오더 컨테이너와 다릅니다 (order_id=%, container=%)', NEW.order_id, NEW.container_no USING ERRCODE = '23514';
  END IF;
  IF NEW.inspected_by_account_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM accounts a WHERE a.account_id = NEW.inspected_by_account_id AND a.user_type IN ('HUB_OP','ADMIN')) THEN
    RAISE EXCEPTION '공컨 점검자는 HUB_OP 또는 ADMIN 이어야 합니다 (account_id=%)', NEW.inspected_by_account_id USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_plan_change_driver()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.driver_account_id IS NOT NULL AND NOT EXISTS (
       SELECT 1 FROM accounts a WHERE a.account_id = NEW.driver_account_id AND a.user_type = 'CONTAINER_DRIVER') THEN
    RAISE EXCEPTION 'CY 변경 확인 기사는 CONTAINER_DRIVER 여야 합니다 (account_id=%)', NEW.driver_account_id USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_fill_return_exception_center()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE v_center BIGINT;
BEGIN
  IF NEW.exception_type IN ('CONTAINER_RETURN_DUE','CONTAINER_RETURN_OVERDUE','RETURN_CY_CHANGED','CONTAINER_DAMAGED') THEN
    IF NEW.source_table = 'transport_orders' THEN
      SELECT h.center_id INTO v_center FROM transport_orders o JOIN hubs h ON h.hub_id = o.dest_hub_id
       WHERE o.order_id::text = NEW.source_key;
    ELSIF NEW.source_table = 'return_plan_changes' THEN
      SELECT h.center_id INTO v_center FROM return_plan_changes p JOIN transport_orders o ON o.order_id = p.order_id
        JOIN hubs h ON h.hub_id = o.dest_hub_id WHERE p.change_id::text = NEW.source_key;
    ELSIF NEW.source_table = 'empty_container_inspections' THEN
      SELECT h.center_id INTO v_center FROM empty_container_inspections i JOIN transport_orders o ON o.order_id = i.order_id
        JOIN hubs h ON h.hub_id = o.dest_hub_id WHERE i.inspection_id::text = NEW.source_key;
    END IF;
    IF v_center IS NULL THEN
      RAISE EXCEPTION '반납 예외의 원본(%:%)을 찾을 수 없습니다', NEW.source_table, NEW.source_key USING ERRCODE = '23503';
    END IF;
    IF NEW.center_id IS NULL THEN NEW.center_id := v_center;
    ELSIF NEW.center_id <> v_center THEN
      RAISE EXCEPTION '반납 예외 거점이 오더 도착 허브 거점과 다릅니다 (%<>%)', NEW.center_id, v_center USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_mark_damage_noted()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  UPDATE transport_orders SET damage_noted = (NEW.result = 'DAMAGED')
   WHERE order_id = NEW.order_id AND damage_noted IS DISTINCT FROM (NEW.result = 'DAMAGED');
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_validate_return_attachment()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM transport_orders o JOIN accounts a ON a.account_id = NEW.uploaded_by_account_id
                  WHERE o.order_id = NEW.order_id
                    AND ((a.user_type = 'CORPORATE_APPROVED' AND a.company_id = o.company_id)
                         OR a.account_id = o.return_driver_account_id)) THEN
    RAISE EXCEPTION '반납 첨부는 오더 운송사 계정 또는 반납 기사만 올릴 수 있습니다 (account_id=%)', NEW.uploaded_by_account_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $function$;
CREATE OR REPLACE FUNCTION scargo_log_center_inbound()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
 IF NEW.leg_type = 'HUB_TO_CENTER' AND NEW.status = 'ARRIVED' AND OLD.status IS DISTINCT FROM 'ARRIVED' THEN
  INSERT INTO center_inbound_logs (center_id, assignment_id, vehicle_no, parcel_count, total_weight_kg, arrived_at)
  SELECT NEW.to_center_id, NEW.assignment_id, NEW.vehicle_no, COALESCE(d.pc, 0), d.kg, COALESCE(NEW.arrived_at, now())
    FROM (SELECT sum(parcel_count)::int AS pc, sum(total_weight_kg) AS kg
            FROM hub_dispatches WHERE assignment_id = NEW.assignment_id) d
  ON CONFLICT (assignment_id) DO NOTHING;
 END IF;
 RETURN NEW;
END $function$;

-- ===== 새 테이블 =====
\restrict DGkel9Devw2ApLG6AibahhjcFv46AFmqvwp9qFWiE6eLSSJr6q4gDvvHSXYI4Ja

CREATE TABLE public.branch_seal_checks (
    check_id bigint NOT NULL,
    assignment_id bigint NOT NULL,
    observed_seal_no character varying(30) NOT NULL,
    expected_seal_no character varying(30),
    matches boolean NOT NULL,
    actor_account_id bigint,
    actor_name character varying(100),
    exception_id bigint,
    checked_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE public.branch_seal_checks IS 'fix_1330: 센터 도착 봉인 확인 입력 이력. 불일치 예외(SEAL_MISMATCH)는 운행당 열린 것 1건만';

CREATE SEQUENCE public.branch_seal_checks_check_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.branch_seal_checks_check_id_seq OWNED BY public.branch_seal_checks.check_id;

CREATE TABLE public.branch_units (
    unit_id character varying(30) NOT NULL,
    assignment_id bigint,
    dest_center_id bigint NOT NULL,
    status character varying(20) DEFAULT 'SORTING'::character varying NOT NULL,
    loaded_at timestamp with time zone,
    unloaded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_units_status CHECK (((status)::text = ANY ((ARRAY['SORTING'::character varying, 'LOADED'::character varying, 'REJECTED'::character varying, 'UNLOADED'::character varying])::text[])))
);

CREATE TABLE public.center_inbound_logs (
    inbound_id bigint NOT NULL,
    center_id bigint NOT NULL,
    assignment_id bigint NOT NULL,
    vehicle_no character varying(20),
    parcel_count integer DEFAULT 0 NOT NULL,
    total_weight_kg numeric(10,1),
    arrived_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT center_inbound_logs_parcel_count_check CHECK ((parcel_count >= 0)),
    CONSTRAINT center_inbound_logs_total_weight_kg_check CHECK (((total_weight_kg IS NULL) OR (total_weight_kg >= (0)::numeric)))
);

COMMENT ON TABLE public.center_inbound_logs IS '물류센터 입고 기록: 지선(허브→센터) 운송이 ARRIVED 되면 트리거가 자동으로 1행 (하루 물량 집계용)';

CREATE SEQUENCE public.center_inbound_logs_inbound_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.center_inbound_logs_inbound_id_seq OWNED BY public.center_inbound_logs.inbound_id;

CREATE TABLE public.empty_container_inspections (
    inspection_id bigint NOT NULL,
    order_id bigint NOT NULL,
    container_no character varying(11) NOT NULL,
    result character varying(10) NOT NULL,
    damage_note text,
    photo_url text,
    inspected_by_account_id bigint,
    inspected_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    damage_location character varying(50),
    damage_type character varying(50),
    CONSTRAINT chk_empty_insp_damage_required CHECK ((((result)::text <> 'DAMAGED'::text) OR ((NULLIF(btrim(photo_url), ''::text) IS NOT NULL) AND (NULLIF(btrim((damage_location)::text), ''::text) IS NOT NULL) AND (NULLIF(btrim((damage_type)::text), ''::text) IS NOT NULL) AND (inspected_by_account_id IS NOT NULL)))),
    CONSTRAINT chk_empty_insp_result CHECK (((result)::text = ANY ((ARRAY['OK'::character varying, 'DAMAGED'::character varying])::text[])))
);

COMMENT ON TABLE public.empty_container_inspections IS '하역 후 공컨테이너 상태 점검 (허브 관제사). OK 여야 반납 운행 시작 가능. v11';

COMMENT ON COLUMN public.empty_container_inspections.photo_url IS '점검 사진. DAMAGED 면 필수이며 컨테이너 번호가 보이게 촬영';

CREATE SEQUENCE public.empty_container_inspections_inspection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.empty_container_inspections_inspection_id_seq OWNED BY public.empty_container_inspections.inspection_id;

CREATE TABLE public.exceptions (
    exception_id bigint NOT NULL,
    exception_type character varying(30) NOT NULL,
    severity character varying(10) DEFAULT 'WARNING'::character varying NOT NULL,
    center_id bigint,
    assignment_id bigint,
    source_table character varying(30),
    source_key character varying(50),
    message text,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    assignee_account_id bigint,
    resolution_note text,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at timestamp with time zone,
    CONSTRAINT chk_exceptions_container_damaged CHECK ((((exception_type)::text <> 'CONTAINER_DAMAGED'::text) OR (((source_table)::text = 'empty_container_inspections'::text) AND ((severity)::text = 'WARNING'::text)))),
    CONSTRAINT chk_exceptions_cy_change_source CHECK ((((exception_type)::text <> 'RETURN_CY_CHANGED'::text) OR (((source_table)::text = 'return_plan_changes'::text) AND ((severity)::text = 'WARNING'::text)))),
    CONSTRAINT chk_exceptions_resolved CHECK ((((status)::text <> 'RESOLVED'::text) OR ((resolved_at IS NOT NULL) AND (NULLIF(btrim(resolution_note), ''::text) IS NOT NULL)))),
    CONSTRAINT chk_exceptions_return_overdue CHECK ((((exception_type)::text <> 'CONTAINER_RETURN_OVERDUE'::text) OR (((source_table)::text = 'transport_orders'::text) AND ((severity)::text = 'CRITICAL'::text)))),
    CONSTRAINT chk_exceptions_return_source CHECK ((((exception_type)::text <> 'CONTAINER_RETURN_DUE'::text) OR ((source_table)::text = 'transport_orders'::text))),
    CONSTRAINT chk_exceptions_seal_unregistered CHECK ((((exception_type)::text <> 'SEAL_UNREGISTERED'::text) OR (((source_table)::text = 'transport_orders'::text) AND ((severity)::text = 'WARNING'::text)))),
    CONSTRAINT chk_exceptions_severity CHECK ((((severity)::text = ANY ((ARRAY['CRITICAL'::character varying, 'WARNING'::character varying])::text[])) AND (((exception_type)::text <> ALL ((ARRAY['SEAL_MISMATCH'::character varying, 'SEAL_DAMAGED'::character varying, 'SEAL_LOST'::character varying, 'OVERLOAD'::character varying])::text[])) OR ((severity)::text = 'CRITICAL'::text)))),
    CONSTRAINT chk_exceptions_source CHECK (((source_table IS NULL) OR ((source_table)::text = ANY ((ARRAY['seal_events'::character varying, 'toll_passages'::character varying, 'parcels'::character varying, 'items'::character varying, 'branch_units'::character varying, 'transport_orders'::character varying, 'return_plan_changes'::character varying, 'empty_container_inspections'::character varying])::text[])))),
    CONSTRAINT chk_exceptions_source_pair CHECK (((source_table IS NULL) = (source_key IS NULL))),
    CONSTRAINT chk_exceptions_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying])::text[]))),
    CONSTRAINT chk_exceptions_type CHECK (((exception_type)::text = ANY ((ARRAY['SEAL_MISMATCH'::character varying, 'SEAL_DAMAGED'::character varying, 'SEAL_LOST'::character varying, 'OVERLOAD'::character varying, 'SORT_FAILED'::character varying, 'ITEM_DAMAGED'::character varying, 'OTHER'::character varying, 'CONTAINER_RETURN_DUE'::character varying, 'RETURN_CY_CHANGED'::character varying, 'CONTAINER_DAMAGED'::character varying, 'CONTAINER_RETURN_OVERDUE'::character varying, 'SEAL_UNREGISTERED'::character varying])::text[])))
);

CREATE SEQUENCE public.exceptions_exception_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.exceptions_exception_id_seq OWNED BY public.exceptions.exception_id;

CREATE TABLE public.ops_settings (
    setting_key character varying(50) NOT NULL,
    setting_label character varying(100) NOT NULL,
    setting_value integer NOT NULL,
    unit character varying(10) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    updated_by bigint,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_ops_settings_return_default_days CHECK ((((setting_key)::text <> 'RETURN_DUE_DEFAULT_DAYS'::text) OR ((setting_value >= 1) AND (setting_value <= 60)))),
    CONSTRAINT ops_settings_setting_value_check CHECK ((setting_value >= 0))
);

CREATE TABLE public.return_attachments (
    attachment_id bigint NOT NULL,
    order_id bigint NOT NULL,
    kind character varying(20) NOT NULL,
    file_url text NOT NULL,
    uploaded_by_account_id bigint NOT NULL,
    uploaded_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_return_attach_kind CHECK (((kind)::text = ANY ((ARRAY['EIR_IN'::character varying, 'DAMAGE_CONFIRM'::character varying])::text[]))),
    CONSTRAINT chk_return_attach_url CHECK ((NULLIF(btrim(file_url), ''::text) IS NOT NULL))
);

COMMENT ON TABLE public.return_attachments IS '공컨 반납 선택 첨부 (CY EIR IN / 손상 확인서). 업로드 = 오더 운송사 계정 또는 반납 기사';

CREATE SEQUENCE public.return_attachments_attachment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.return_attachments_attachment_id_seq OWNED BY public.return_attachments.attachment_id;

CREATE TABLE public.return_cys (
    cy_id bigint NOT NULL,
    cy_code character varying(20) NOT NULL,
    cy_name character varying(100) NOT NULL,
    terminal_id bigint,
    address text,
    latitude numeric(10,7),
    longitude numeric(10,7),
    is_active boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_demo boolean DEFAULT false NOT NULL
);

COMMENT ON TABLE public.return_cys IS '공컨테이너 반납 CY 목록 (W-07 선택 · 앱 T-03 표시). v11';

COMMENT ON COLUMN public.return_cys.is_demo IS '데모(가상) CY. 13장 시드 4곳은 TRUE — 실제 선사별 CY 로 교체 필요';

CREATE SEQUENCE public.return_cys_cy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.return_cys_cy_id_seq OWNED BY public.return_cys.cy_id;

CREATE TABLE public.return_plan_changes (
    change_id bigint NOT NULL,
    order_id bigint NOT NULL,
    field character varying(10) NOT NULL,
    old_value text,
    new_value text NOT NULL,
    old_cy_id bigint,
    new_cy_id bigint,
    old_due_at timestamp with time zone,
    new_due_at timestamp with time zone,
    reason text NOT NULL,
    changed_by_account_id bigint,
    changed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    return_status_at_change character varying(20),
    driver_account_id bigint,
    driver_confirmed_at timestamp with time zone,
    CONSTRAINT chk_plan_change_confirm CHECK (((driver_confirmed_at IS NULL) OR (driver_account_id IS NOT NULL))),
    CONSTRAINT chk_plan_change_due_no_confirm CHECK ((((field)::text <> 'DUE'::text) OR (driver_confirmed_at IS NULL))),
    CONSTRAINT chk_plan_change_field CHECK (((field)::text = ANY ((ARRAY['CY'::character varying, 'DUE'::character varying])::text[]))),
    CONSTRAINT chk_plan_change_reason CHECK ((NULLIF(btrim(reason), ''::text) IS NOT NULL)),
    CONSTRAINT chk_plan_change_values CHECK (((((field)::text = 'CY'::text) AND (new_cy_id IS NOT NULL)) OR (((field)::text = 'DUE'::text) AND (new_due_at IS NOT NULL))))
);

COMMENT ON TABLE public.return_plan_changes IS '공컨 반납 CY·기한 변경 이력 (W-07). 반납 시작 뒤 CY 변경은 기사 확인 필요 (RETURN_CY_CHANGED 예외). v11';

COMMENT ON COLUMN public.return_plan_changes.driver_confirmed_at IS 'CY 변경만 기사 확인. DUE 변경은 확인 없이 RETURN_PLAN_CHANGED 알림만';

CREATE SEQUENCE public.return_plan_changes_change_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.return_plan_changes_change_id_seq OWNED BY public.return_plan_changes.change_id;

CREATE TABLE public.seal_unregistered_inputs (
    input_id bigint NOT NULL,
    order_id bigint NOT NULL,
    container_no character varying(11) NOT NULL,
    observed_seal_no character varying(30) NOT NULL,
    stage character varying(20) NOT NULL,
    actor_account_id bigint,
    actor_name character varying(100),
    exception_id bigint,
    occurred_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_seal_unreg_stage CHECK (((stage)::text = ANY ((ARRAY['PICKUP'::character varying, 'HANDOVER'::character varying])::text[])))
);

COMMENT ON TABLE public.seal_unregistered_inputs IS 'fix_1300: 정답 씰(container_seals ACTIVE)이 없을 때의 현장 씰 입력. 자동 매칭·자동 정답 생성 안 함. SEAL_UNREGISTERED(WARNING) 예외와 연결';

CREATE SEQUENCE public.seal_unregistered_inputs_input_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.seal_unregistered_inputs_input_id_seq OWNED BY public.seal_unregistered_inputs.input_id;

CREATE TABLE public.toll_fee_rules (
    vehicle_class smallint NOT NULL,
    class_name character varying(10) NOT NULL,
    axle_desc character varying(20) NOT NULL,
    base_won integer DEFAULT 900 NOT NULL,
    per_km_won numeric(6,1) NOT NULL,
    is_virtual boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT toll_fee_rules_base_won_check CHECK ((base_won >= 0)),
    CONSTRAINT toll_fee_rules_per_km_won_check CHECK ((per_km_won >= (0)::numeric)),
    CONSTRAINT toll_fee_rules_vehicle_class_check CHECK (((vehicle_class >= 1) AND (vehicle_class <= 5)))
);

COMMENT ON TABLE public.toll_fee_rules IS '차종별 고속도로 요금 (W-14). 전 행 데모(가상) 요율, is_virtual=TRUE. 2종 45.7은 1·3종 중간값으로 만든 데모 값';

ALTER TABLE ONLY public.branch_seal_checks ALTER COLUMN check_id SET DEFAULT nextval('public.branch_seal_checks_check_id_seq'::regclass);

ALTER TABLE ONLY public.center_inbound_logs ALTER COLUMN inbound_id SET DEFAULT nextval('public.center_inbound_logs_inbound_id_seq'::regclass);

ALTER TABLE ONLY public.empty_container_inspections ALTER COLUMN inspection_id SET DEFAULT nextval('public.empty_container_inspections_inspection_id_seq'::regclass);

ALTER TABLE ONLY public.exceptions ALTER COLUMN exception_id SET DEFAULT nextval('public.exceptions_exception_id_seq'::regclass);

ALTER TABLE ONLY public.return_attachments ALTER COLUMN attachment_id SET DEFAULT nextval('public.return_attachments_attachment_id_seq'::regclass);

ALTER TABLE ONLY public.return_cys ALTER COLUMN cy_id SET DEFAULT nextval('public.return_cys_cy_id_seq'::regclass);

ALTER TABLE ONLY public.return_plan_changes ALTER COLUMN change_id SET DEFAULT nextval('public.return_plan_changes_change_id_seq'::regclass);

ALTER TABLE ONLY public.seal_unregistered_inputs ALTER COLUMN input_id SET DEFAULT nextval('public.seal_unregistered_inputs_input_id_seq'::regclass);

ALTER TABLE ONLY public.branch_seal_checks
    ADD CONSTRAINT branch_seal_checks_pkey PRIMARY KEY (check_id);

ALTER TABLE ONLY public.branch_units
    ADD CONSTRAINT branch_units_pkey PRIMARY KEY (unit_id);

ALTER TABLE ONLY public.center_inbound_logs
    ADD CONSTRAINT center_inbound_logs_assignment_id_key UNIQUE (assignment_id);

ALTER TABLE ONLY public.center_inbound_logs
    ADD CONSTRAINT center_inbound_logs_pkey PRIMARY KEY (inbound_id);

ALTER TABLE ONLY public.empty_container_inspections
    ADD CONSTRAINT empty_container_inspections_order_id_key UNIQUE (order_id);

ALTER TABLE ONLY public.empty_container_inspections
    ADD CONSTRAINT empty_container_inspections_pkey PRIMARY KEY (inspection_id);

ALTER TABLE ONLY public.exceptions
    ADD CONSTRAINT exceptions_pkey PRIMARY KEY (exception_id);

ALTER TABLE ONLY public.ops_settings
    ADD CONSTRAINT ops_settings_pkey PRIMARY KEY (setting_key);

ALTER TABLE ONLY public.return_attachments
    ADD CONSTRAINT return_attachments_pkey PRIMARY KEY (attachment_id);

ALTER TABLE ONLY public.return_cys
    ADD CONSTRAINT return_cys_cy_code_key UNIQUE (cy_code);

ALTER TABLE ONLY public.return_cys
    ADD CONSTRAINT return_cys_pkey PRIMARY KEY (cy_id);

ALTER TABLE ONLY public.return_plan_changes
    ADD CONSTRAINT return_plan_changes_pkey PRIMARY KEY (change_id);

ALTER TABLE ONLY public.seal_unregistered_inputs
    ADD CONSTRAINT seal_unregistered_inputs_pkey PRIMARY KEY (input_id);

ALTER TABLE ONLY public.toll_fee_rules
    ADD CONSTRAINT toll_fee_rules_pkey PRIMARY KEY (vehicle_class);

ALTER TABLE ONLY public.exceptions
    ADD CONSTRAINT uk_exceptions_source UNIQUE (source_table, source_key, exception_type);

CREATE INDEX idx_branch_seal_checks_leg ON public.branch_seal_checks USING btree (assignment_id, check_id DESC);

CREATE INDEX idx_center_inbound_time ON public.center_inbound_logs USING btree (center_id, arrived_at DESC);

CREATE INDEX idx_exceptions_status_center_time ON public.exceptions USING btree (status, center_id, occurred_at DESC);

CREATE INDEX idx_plan_changes_order ON public.return_plan_changes USING btree (order_id, changed_at DESC);

CREATE INDEX idx_return_attach_order ON public.return_attachments USING btree (order_id);

CREATE INDEX idx_seal_unreg_container ON public.seal_unregistered_inputs USING btree (container_no, occurred_at DESC);

CREATE INDEX idx_units_assignment ON public.branch_units USING btree (assignment_id);

CREATE TRIGGER trg_branch_unit_leg BEFORE INSERT OR UPDATE OF assignment_id ON public.branch_units FOR EACH ROW EXECUTE FUNCTION public.scargo_validate_branch_unit();

CREATE TRIGGER trg_empty_inspection_damage_noted AFTER INSERT OR UPDATE OF result ON public.empty_container_inspections FOR EACH ROW EXECUTE FUNCTION public.scargo_mark_damage_noted();

CREATE TRIGGER trg_empty_inspection_validate BEFORE INSERT OR UPDATE ON public.empty_container_inspections FOR EACH ROW EXECUTE FUNCTION public.scargo_validate_empty_inspection();

CREATE TRIGGER trg_exceptions_return_center BEFORE INSERT OR UPDATE OF exception_type, source_table, source_key, center_id ON public.exceptions FOR EACH ROW EXECUTE FUNCTION public.scargo_fill_return_exception_center();

CREATE TRIGGER trg_notify_seal_lost AFTER INSERT ON public.exceptions FOR EACH ROW EXECUTE FUNCTION public.scargo_notify_seal_lost();

CREATE TRIGGER trg_plan_change_driver BEFORE INSERT OR UPDATE OF driver_account_id ON public.return_plan_changes FOR EACH ROW EXECUTE FUNCTION public.scargo_validate_plan_change_driver();

CREATE TRIGGER trg_return_attachment_validate BEFORE INSERT OR UPDATE OF order_id, uploaded_by_account_id ON public.return_attachments FOR EACH ROW EXECUTE FUNCTION public.scargo_validate_return_attachment();

ALTER TABLE ONLY public.branch_seal_checks
    ADD CONSTRAINT branch_seal_checks_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(account_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.branch_seal_checks
    ADD CONSTRAINT branch_seal_checks_assignment_id_fkey FOREIGN KEY (assignment_id) REFERENCES public.shipment_legs(assignment_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.branch_seal_checks
    ADD CONSTRAINT branch_seal_checks_exception_id_fkey FOREIGN KEY (exception_id) REFERENCES public.exceptions(exception_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.branch_units
    ADD CONSTRAINT branch_units_assignment_id_fkey FOREIGN KEY (assignment_id) REFERENCES public.shipment_legs(assignment_id);

ALTER TABLE ONLY public.branch_units
    ADD CONSTRAINT branch_units_dest_center_id_fkey FOREIGN KEY (dest_center_id) REFERENCES public.logistics_centers(center_id);

ALTER TABLE ONLY public.center_inbound_logs
    ADD CONSTRAINT center_inbound_logs_assignment_id_fkey FOREIGN KEY (assignment_id) REFERENCES public.shipment_legs(assignment_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.center_inbound_logs
    ADD CONSTRAINT center_inbound_logs_center_id_fkey FOREIGN KEY (center_id) REFERENCES public.logistics_centers(center_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.empty_container_inspections
    ADD CONSTRAINT empty_container_inspections_container_no_fkey FOREIGN KEY (container_no) REFERENCES public.containers(container_no);

ALTER TABLE ONLY public.empty_container_inspections
    ADD CONSTRAINT empty_container_inspections_inspected_by_account_id_fkey FOREIGN KEY (inspected_by_account_id) REFERENCES public.accounts(account_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.empty_container_inspections
    ADD CONSTRAINT empty_container_inspections_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.transport_orders(order_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.exceptions
    ADD CONSTRAINT exceptions_assignee_account_id_fkey FOREIGN KEY (assignee_account_id) REFERENCES public.accounts(account_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.exceptions
    ADD CONSTRAINT exceptions_assignment_id_fkey FOREIGN KEY (assignment_id) REFERENCES public.shipment_legs(assignment_id);

ALTER TABLE ONLY public.exceptions
    ADD CONSTRAINT exceptions_center_id_fkey FOREIGN KEY (center_id) REFERENCES public.logistics_centers(center_id);

ALTER TABLE ONLY public.ops_settings
    ADD CONSTRAINT ops_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.accounts(account_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.return_attachments
    ADD CONSTRAINT return_attachments_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.transport_orders(order_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.return_attachments
    ADD CONSTRAINT return_attachments_uploaded_by_account_id_fkey FOREIGN KEY (uploaded_by_account_id) REFERENCES public.accounts(account_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.return_cys
    ADD CONSTRAINT return_cys_terminal_id_fkey FOREIGN KEY (terminal_id) REFERENCES public.terminals(terminal_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.return_plan_changes
    ADD CONSTRAINT return_plan_changes_changed_by_account_id_fkey FOREIGN KEY (changed_by_account_id) REFERENCES public.accounts(account_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.return_plan_changes
    ADD CONSTRAINT return_plan_changes_driver_account_id_fkey FOREIGN KEY (driver_account_id) REFERENCES public.accounts(account_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.return_plan_changes
    ADD CONSTRAINT return_plan_changes_new_cy_id_fkey FOREIGN KEY (new_cy_id) REFERENCES public.return_cys(cy_id);

ALTER TABLE ONLY public.return_plan_changes
    ADD CONSTRAINT return_plan_changes_old_cy_id_fkey FOREIGN KEY (old_cy_id) REFERENCES public.return_cys(cy_id);

ALTER TABLE ONLY public.return_plan_changes
    ADD CONSTRAINT return_plan_changes_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.transport_orders(order_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.seal_unregistered_inputs
    ADD CONSTRAINT seal_unregistered_inputs_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(account_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.seal_unregistered_inputs
    ADD CONSTRAINT seal_unregistered_inputs_exception_id_fkey FOREIGN KEY (exception_id) REFERENCES public.exceptions(exception_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.seal_unregistered_inputs
    ADD CONSTRAINT seal_unregistered_inputs_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.transport_orders(order_id) ON DELETE RESTRICT;

\unrestrict DGkel9Devw2ApLG6AibahhjcFv46AFmqvwp9qFWiE6eLSSJr6q4gDvvHSXYI4Ja

-- ===== 기존 테이블 제약 · 인덱스 추가 =====
ALTER TABLE companies ADD CONSTRAINT fk_companies_approved_by FOREIGN KEY (approved_by_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE companies ADD CONSTRAINT chk_companies_approval_status CHECK (((approval_status IS NULL) OR ((approval_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))));
ALTER TABLE companies ADD CONSTRAINT chk_companies_approved_at CHECK ((((approval_status)::text IS DISTINCT FROM 'APPROVED'::text) OR ((approved_at IS NOT NULL) AND (approved_by_account_id IS NOT NULL))));
ALTER TABLE companies ADD CONSTRAINT chk_companies_reject_reason CHECK ((((approval_status)::text IS DISTINCT FROM 'REJECTED'::text) OR (NULLIF(btrim(reject_reason), ''::text) IS NOT NULL)));
ALTER TABLE shipment_legs ADD CONSTRAINT fk_legs_driver FOREIGN KEY (driver_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE shipment_legs ADD CONSTRAINT chk_legs_planned_units CHECK (((planned_units IS NULL) OR (planned_units >= 0)));
ALTER TABLE parcels ADD CONSTRAINT fk_parcels_unit FOREIGN KEY (unit_id) REFERENCES branch_units(unit_id) ON DELETE SET NULL;
ALTER TABLE hub_vehicles ADD CONSTRAINT fk_hub_vehicles_company FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE SET NULL;
ALTER TABLE hub_vehicles ADD CONSTRAINT fk_hub_vehicles_truck FOREIGN KEY (vehicle_no) REFERENCES trucks(vehicle_no) ON DELETE SET NULL;
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_approved_by FOREIGN KEY (approved_by_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_approval_status CHECK (((approval_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])));
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_approval_driver_only CHECK ((((approval_status)::text = 'APPROVED'::text) OR ((user_type)::text = ANY ((ARRAY['CONTAINER_DRIVER'::character varying, 'BRANCH_DRIVER'::character varying])::text[]))));
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_reject_reason CHECK ((((approval_status)::text <> 'REJECTED'::text) OR (NULLIF(btrim(reject_reason), ''::text) IS NOT NULL)));
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_approval_fields CHECK (((((approval_status)::text = 'PENDING'::text) AND (approved_by_account_id IS NULL) AND (approved_at IS NULL)) OR (((approval_status)::text <> 'PENDING'::text) AND ((approved_by_account_id IS NULL) OR (approved_at IS NOT NULL)))));
ALTER TABLE transport_orders ADD CONSTRAINT chk_transport_orders_return_after_handover CHECK (((return_status IS NULL) OR ((status)::text = 'HANDED_OVER'::text)));
ALTER TABLE transport_orders ADD CONSTRAINT fk_orders_return_cy FOREIGN KEY (return_cy_id) REFERENCES return_cys(cy_id) ON DELETE RESTRICT;
ALTER TABLE transport_orders ADD CONSTRAINT fk_orders_return_driver FOREIGN KEY (return_driver_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE transport_orders ADD CONSTRAINT fk_orders_return_instructed_by_account FOREIGN KEY (return_instructed_by_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE transport_orders ADD CONSTRAINT chk_orders_return_proof CHECK ((((return_status)::text IS DISTINCT FROM 'EMPTY_RETURNED'::text) OR ((NULLIF(btrim((return_receiver_name)::text), ''::text) IS NOT NULL) AND (NULLIF(btrim((return_eir_no)::text), ''::text) IS NOT NULL) AND (return_photo_url IS NOT NULL))));
ALTER TABLE transport_orders ADD CONSTRAINT chk_orders_return_instruction CHECK (((return_driver_account_id IS NULL) OR ((return_instructed_at IS NOT NULL) AND (return_ready_at IS NOT NULL))));
ALTER TABLE transport_orders ADD CONSTRAINT chk_orders_return_instructor CHECK (((return_instructed_by_account_id IS NULL) OR (return_instructed_at IS NOT NULL)));
ALTER TABLE transport_orders ADD CONSTRAINT chk_transport_orders_return_status CHECK (((return_status IS NULL) OR ((return_status)::text = ANY ((ARRAY['RETURNING_EMPTY'::character varying, 'RETURN_ARRIVED'::character varying, 'EMPTY_RETURNED'::character varying])::text[]))));
ALTER TABLE transport_orders ADD CONSTRAINT chk_transport_orders_return_steps CHECK ((((return_status IS NULL) AND (return_started_at IS NULL) AND (return_arrived_at IS NULL) AND (empty_returned_at IS NULL)) OR (((return_status)::text = 'RETURNING_EMPTY'::text) AND (return_started_at IS NOT NULL) AND (return_arrived_at IS NULL) AND (empty_returned_at IS NULL)) OR (((return_status)::text = 'RETURN_ARRIVED'::text) AND (return_started_at IS NOT NULL) AND (return_arrived_at IS NOT NULL) AND (empty_returned_at IS NULL)) OR (((return_status)::text = 'EMPTY_RETURNED'::text) AND (return_started_at IS NOT NULL) AND (return_arrived_at IS NOT NULL) AND (empty_returned_at IS NOT NULL))));
ALTER TABLE shipment_legs ADD CONSTRAINT chk_leg_branch_cols CHECK ((((leg_type)::text = 'HUB_TO_CENTER'::text) OR ((domestic_seal_no IS NULL) AND (planned_units IS NULL) AND (hub_arrived_at IS NULL) AND (arrival_seal_no IS NULL))));
ALTER TABLE companies ADD CONSTRAINT chk_companies_carrier_approval CHECK (((NOT is_carrier) OR (approval_status IS NOT NULL)));
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_user_type;
ALTER TABLE accounts ADD CONSTRAINT chk_user_type CHECK (user_type::text = ANY (ARRAY['ADMIN','CONTROL','HUB_OP','CENTER_OP','CONTAINER_DRIVER','LINEHAUL_DRIVER','BRANCH_DRIVER','CORPORATE_APPROVED','GENERAL','CORPORATE_PENDING']));
CREATE INDEX IF NOT EXISTS idx_accounts_pending_drivers ON accounts USING btree (company_id) WHERE ((approval_status)::text = 'PENDING'::text);
CREATE INDEX IF NOT EXISTS idx_hub_vehicles_company ON hub_vehicles USING btree (company_id);
CREATE INDEX IF NOT EXISTS idx_legs_driver ON shipment_legs USING btree (driver_account_id, status);
CREATE INDEX IF NOT EXISTS idx_notifications_company_type ON notifications USING btree (company_id, notification_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_return_driver ON transport_orders USING btree (return_driver_account_id) WHERE (return_driver_account_id IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_orders_return_due ON transport_orders USING btree (return_due_at) WHERE ((return_due_at IS NOT NULL) AND ((return_status)::text IS DISTINCT FROM 'EMPTY_RETURNED'::text));
CREATE INDEX IF NOT EXISTS idx_parcels_unit ON parcels USING btree (unit_id);

-- ===== 뷰 =====
CREATE OR REPLACE VIEW v_carrier_contacts AS
 SELECT a.account_id,
    a.user_id,
    a.company_id,
    c.approval_status,
    ((c.approval_status)::text = 'APPROVED'::text) AS can_use_carrier_screens
   FROM (accounts a
     JOIN companies c ON ((c.company_id = a.company_id)))
  WHERE (((a.user_type)::text = 'CORPORATE_APPROVED'::text) AND c.is_carrier);;
CREATE OR REPLACE VIEW v_site_urgency AS
 SELECT c.center_id,
    c.center_name,
    c.center_type,
    count(e.exception_id) FILTER (WHERE ((e.severity)::text = 'CRITICAL'::text)) AS open_critical_count,
    count(e.exception_id) FILTER (WHERE ((e.severity)::text = 'WARNING'::text)) AS open_warning_count,
    (count(e.exception_id) FILTER (WHERE ((e.severity)::text = 'CRITICAL'::text)) >= 1) AS is_urgent
   FROM (logistics_centers c
     LEFT JOIN exceptions e ON (((e.center_id = c.center_id) AND ((e.status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying])::text[])))))
  GROUP BY c.center_id, c.center_name, c.center_type;;
CREATE OR REPLACE VIEW v_center_daily_inbound AS
 SELECT c.center_id,
    c.center_name,
    ((i.arrived_at AT TIME ZONE 'Asia/Seoul'::text))::date AS inbound_date,
    count(*) AS trucks,
    sum(i.parcel_count) AS parcels,
    COALESCE(sum(i.total_weight_kg), (0)::numeric) AS weight_kg
   FROM (center_inbound_logs i
     JOIN logistics_centers c ON ((c.center_id = i.center_id)))
  GROUP BY c.center_id, c.center_name, (((i.arrived_at AT TIME ZONE 'Asia/Seoul'::text))::date);;
CREATE OR REPLACE VIEW v_center_hourly_inbound AS
 SELECT c.center_id,
    c.center_name,
    ((i.arrived_at AT TIME ZONE 'Asia/Seoul'::text))::date AS inbound_date,
    (EXTRACT(hour FROM (i.arrived_at AT TIME ZONE 'Asia/Seoul'::text)))::integer AS inbound_hour,
    count(*) AS trucks,
    sum(i.parcel_count) AS parcels
   FROM (center_inbound_logs i
     JOIN logistics_centers c ON ((c.center_id = i.center_id)))
  GROUP BY c.center_id, c.center_name, (((i.arrived_at AT TIME ZONE 'Asia/Seoul'::text))::date), ((EXTRACT(hour FROM (i.arrived_at AT TIME ZONE 'Asia/Seoul'::text)))::integer);;

-- ===== 트리거 =====
CREATE TRIGGER trg_account_carrier_contact BEFORE INSERT OR UPDATE OF user_type, company_id ON accounts FOR EACH ROW EXECUTE FUNCTION scargo_validate_carrier_contact();
CREATE TRIGGER trg_company_carrier_guard BEFORE UPDATE OF is_carrier ON companies FOR EACH ROW EXECUTE FUNCTION scargo_guard_carrier_company();
CREATE TRIGGER trg_company_approver BEFORE INSERT OR UPDATE OF approved_by_account_id, approval_status ON companies FOR EACH ROW EXECUTE FUNCTION scargo_validate_company_approver();
CREATE TRIGGER trg_notify_overload_block AFTER INSERT OR UPDATE OF passage_status ON toll_passages FOR EACH ROW EXECUTE FUNCTION scargo_notify_overload_block();
CREATE TRIGGER trg_account_approver BEFORE INSERT OR UPDATE OF approved_by_account_id, approval_status, company_id ON accounts FOR EACH ROW EXECUTE FUNCTION scargo_validate_account_approver();
CREATE TRIGGER trg_orders_return_start BEFORE INSERT OR UPDATE OF return_status ON transport_orders FOR EACH ROW EXECUTE FUNCTION scargo_validate_return_start();
CREATE TRIGGER trg_orders_return_roles BEFORE INSERT OR UPDATE OF return_driver_account_id, return_instructed_by_account_id, company_id ON transport_orders FOR EACH ROW EXECUTE FUNCTION scargo_validate_return_roles();
CREATE TRIGGER trg_log_center_inbound AFTER UPDATE OF status ON shipment_legs FOR EACH ROW EXECUTE FUNCTION scargo_log_center_inbound();

-- ===== 기준 데이터 =====
\restrict rA5MdjExoLAb7L0JYSReaDqpWj5P8cfyMDZchDFjJW3CTISTXJfQ7jGLLc0MjAg

INSERT INTO public.ops_settings (setting_key, setting_label, setting_value, unit, sort_order, updated_by, updated_at) VALUES ('WARN_DELAY_MIN', '주의 판정 지연 기준', 30, '분', 1, NULL, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.ops_settings (setting_key, setting_label, setting_value, unit, sort_order, updated_by, updated_at) VALUES ('ARRIVAL_DELAY_MIN', '도착 지연 예외', 30, '분', 2, NULL, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.ops_settings (setting_key, setting_label, setting_value, unit, sort_order, updated_by, updated_at) VALUES ('DOCK_WAIT_MIN', '도크 대기 예외', 20, '분', 3, NULL, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.ops_settings (setting_key, setting_label, setting_value, unit, sort_order, updated_by, updated_at) VALUES ('GPS_LOST_MIN', 'GPS 끊김 예외', 10, '분', 4, NULL, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.ops_settings (setting_key, setting_label, setting_value, unit, sort_order, updated_by, updated_at) VALUES ('RETURN_DUE_SOON_HOURS', '반납 기한 "임박" 기준', 24, '시간 전', 5, NULL, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.ops_settings (setting_key, setting_label, setting_value, unit, sort_order, updated_by, updated_at) VALUES ('RETURN_DUE_DEFAULT_DAYS', '반납 기한 기본값 (미입력 시)', 7, '일', 6, NULL, '2026-10-26 11:10:20.969+09') ON CONFLICT DO NOTHING;
INSERT INTO public.ops_settings (setting_key, setting_label, setting_value, unit, sort_order, updated_by, updated_at) VALUES ('POLL_SEC', '관제 폴링 간격', 30, '초', 7, NULL, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;

INSERT INTO public.return_cys (cy_id, cy_code, cy_name, terminal_id, address, latitude, longitude, is_active, sort_order, created_at, is_demo) VALUES (1, 'ICNP-CY', '인천신항 터미널 CY', 13, '인천 연수구 송도동 (가상)', 37.3480000, 126.6380000, true, 1, '2026-10-26 11:10:20.908379+09', true) ON CONFLICT DO NOTHING;
INSERT INTO public.return_cys (cy_id, cy_code, cy_name, terminal_id, address, latitude, longitude, is_active, sort_order, created_at, is_demo) VALUES (2, 'ICN-SH-CY', '인천 남항 공컨 CY', NULL, '인천 중구 항동 (가상)', 37.4560000, 126.6100000, true, 2, '2026-10-26 11:10:20.908379+09', true) ON CONFLICT DO NOTHING;
INSERT INTO public.return_cys (cy_id, cy_code, cy_name, terminal_id, address, latitude, longitude, is_active, sort_order, created_at, is_demo) VALUES (3, 'ICN-NP-CY', '인천 북항 공컨 CY', NULL, '인천 서구 원창동 (가상)', 37.5120000, 126.6300000, true, 3, '2026-10-26 11:10:20.908379+09', true) ON CONFLICT DO NOTHING;
INSERT INTO public.return_cys (cy_id, cy_code, cy_name, terminal_id, address, latitude, longitude, is_active, sort_order, created_at, is_demo) VALUES (4, 'UIW-ICD', '의왕 ICD 공컨 CY', NULL, '경기 의왕시 이동 (가상)', 37.3330000, 126.9690000, true, 4, '2026-10-26 11:10:20.908379+09', true) ON CONFLICT DO NOTHING;

INSERT INTO public.toll_fee_rules (vehicle_class, class_name, axle_desc, base_won, per_km_won, is_virtual, updated_at) VALUES (1, '1종', '2축', 900, 44.3, true, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.toll_fee_rules (vehicle_class, class_name, axle_desc, base_won, per_km_won, is_virtual, updated_at) VALUES (3, '3종', '3축', 900, 47.0, true, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.toll_fee_rules (vehicle_class, class_name, axle_desc, base_won, per_km_won, is_virtual, updated_at) VALUES (4, '4종', '4축', 900, 62.9, true, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.toll_fee_rules (vehicle_class, class_name, axle_desc, base_won, per_km_won, is_virtual, updated_at) VALUES (5, '5종', '5축+', 900, 74.4, true, '2026-10-26 11:10:20.897028+09') ON CONFLICT DO NOTHING;
INSERT INTO public.toll_fee_rules (vehicle_class, class_name, axle_desc, base_won, per_km_won, is_virtual, updated_at) VALUES (2, '2종', '2축(광폭)', 900, 45.7, true, '2026-10-26 11:10:20.930132+09') ON CONFLICT DO NOTHING;

SELECT pg_catalog.setval('public.return_cys_cy_id_seq', 4, true);

\unrestrict rA5MdjExoLAb7L0JYSReaDqpWj5P8cfyMDZchDFjJW3CTISTXJfQ7jGLLc0MjAg
