package com.scargo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler { //에러메시지 통합 출력용

	
    /** fix_1517: @Valid 실패 → "Validation failed for object=..." 대신 필드별 한글 메시지 */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(org.springframework.web.bind.MethodArgumentNotValidException e) {
        java.util.List<Map<String, String>> fields = new java.util.ArrayList<>();
        for (org.springframework.validation.FieldError fe : e.getBindingResult().getFieldErrors())
            fields.add(Map.of("field", fe.getField(), "message", fe.getDefaultMessage() == null ? "값이 올바르지 않습니다" : fe.getDefaultMessage()));
        String msg = fields.isEmpty() ? "입력값이 올바르지 않습니다." : fields.get(0).get("message");
        return ResponseEntity.badRequest().body(Map.of("message", msg, "fields", fields));
    }


    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    /** 요청 주소와 글자 그대로 같은(경로 변수 없는) 매핑의 허용 방식 목록. 없으면 null */
    private String literalPathOtherMethod(jakarta.servlet.http.HttpServletRequest req) {
        if (handlerMapping == null) return null;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        java.util.Set<String> methods = new java.util.TreeSet<>();
        for (var info : handlerMapping.getHandlerMethods().keySet()) {
            var pc = info.getPathPatternsCondition();
            if (pc == null) continue;
            boolean literal = pc.getPatternValues().stream().anyMatch(v -> v.equals(path) && !v.contains("{") && !v.contains("*"));
            if (!literal) continue;
            var ms = info.getMethodsCondition().getMethods();
            if (ms.isEmpty()) return null; // 모든 방식 허용 매핑이면 405 아님
            ms.forEach(m -> methods.add(m.name()));
        }
        methods.remove(req.getMethod());
        return methods.isEmpty() ? null : String.join(", ", methods);
    }

    // fix_1420: 요청 형식 오류(잘못된 JSON·숫자·타입·필수 파라미터 누락) → 공통 한글 문구 (영문 원문 노출 금지)
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception e, jakarta.servlet.http.HttpServletRequest req) {
        // fix_1450: 경로 변수 타입 오류인데 같은 주소의 고정 경로(변수 없는) 매핑이 다른 방식으로 있으면 405 (예: PUT /api/accounts/login)
        if (e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException tm
                && tm.getParameter().hasParameterAnnotation(org.springframework.web.bind.annotation.PathVariable.class)) {
            String other = literalPathOtherMethod(req);
            if (other != null) return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header("Allow", other)
                    .body(Map.of("message", "지원하지 않는 요청 방식입니다 (" + req.getMethod() + ")."));
        }
        String m = e instanceof org.springframework.web.bind.MissingServletRequestParameterException mp ? "필수 값이 빠졌습니다 (" + mp.getParameterName() + ")."
                : e instanceof org.springframework.web.multipart.support.MissingServletRequestPartException mp ? "필수 파일이 빠졌습니다 (" + mp.getRequestPartName() + ")."
                : e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ? "요청 값의 형식이 올바르지 않습니다. 숫자 등 입력값을 확인하세요."
                : "요청 형식이 올바르지 않습니다.";
        return ResponseEntity.badRequest().body(Map.of("message", m));
    }

    // fix_1440: 404·405·415 → 공통 한글 문구 (Spring 기본 영문 문구 대신)
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class, org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "요청한 주소를 찾을 수 없습니다."));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethod(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("message", "지원하지 않는 요청 방식입니다 (" + e.getMethod() + ")."));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMedia(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("message", "지원하지 않는 요청 형식입니다. 보내는 데이터 형식을 확인하세요."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        if (e instanceof NumberFormatException) // fix_1420: "For input string: …" 대신
            return ResponseEntity.badRequest().body(Map.of("message", "숫자 형식이 올바르지 않습니다. 입력값을 확인하세요."));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    // 26.10.01 추가(계중대 정식화): 이미 계량된 차량을 다시 저장하는 등 "상태가 맞지 않는" 요청 → 409
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }


    // fix_1400: DB 제약 오류(영문/원문) → 사용자용 한글 문구. 트리거가 RAISE 한 한글 메시지는 그대로 둔다
    private static final Map<String, String> CONSTRAINT_MSG = Map.ofEntries(
            Map.entry("uk_legs_active_vehicle", "이 차량은 진행 중인 운행이 있습니다. 운행을 마친 뒤 다시 배차하세요."),
            Map.entry("uk_orders_active_vehicle", "이 차량은 진행 중인 운송오더가 있습니다. 오더를 마친 뒤 다시 등록하세요."),
            Map.entry("uk_order_active_container", "이 컨테이너는 이미 진행 중인 운송오더가 있습니다."),
            Map.entry("uk_legs_active_container_trip", "이 컨테이너는 이미 진행 중인 운행에 배정되어 있습니다."),
            Map.entry("uk_loading_active_container", "이 컨테이너는 이미 상차 진행 중입니다."),
            Map.entry("uk_legs_shipment_seq", "같은 화물에 같은 순번의 구간이 이미 있습니다."),
            Map.entry("uk_orders_leg", "이 구간에는 이미 운송오더가 연결되어 있습니다."),
            Map.entry("uk_dispatches_assignment", "이 운행은 이미 배차되었습니다."),
            Map.entry("uk_receipts_active_dock", "이 도크는 이미 다른 차량이 사용 중입니다."),
            Map.entry("uk_receipts_assignment", "이 운행은 이미 입고 처리되었습니다."),
            Map.entry("uk_seals_active_position", "이 위치에는 이미 사용 중인 씰이 있습니다."),
            Map.entry("uk_seals_trip_number", "같은 운행에 같은 씰 번호가 이미 등록되어 있습니다."),
            Map.entry("uk_seals_previous_once", "이 씰은 이미 교체 처리되었습니다."),
            Map.entry("uk_transport_orders_id_shipment", "이 화물(씰)은 이미 다른 운송오더에 연결되어 있습니다."),
            Map.entry("uk_company_name_address", "같은 이름·주소의 회사가 이미 등록되어 있습니다."),
            Map.entry("uk_center_region", "이 지역에는 이미 센터가 등록되어 있습니다."),
            Map.entry("uk_docks_hub_code", "같은 허브에 같은 도크 코드가 이미 있습니다."),
            Map.entry("uk_slots_position", "이 위치에는 이미 슬롯이 있습니다."),
            Map.entry("uk_zones_hub_tag", "같은 허브에 같은 구역 태그가 이미 있습니다."),
            Map.entry("uk_vehicle_daily_logs_day", "이 차량의 해당 날짜 기록이 이미 있습니다."),
            Map.entry("uk_toll_passages_exit_passed", "이미 기록된 톨게이트 통과 정보입니다."),
            Map.entry("uk_exceptions_source", "같은 예외가 이미 등록되어 있습니다."));

    static String friendly(Throwable root, String raw) {
        String m = raw == null ? "" : raw;
        int cut = m.indexOf('\n'); if (cut > 0) m = m.substring(0, cut);
        m = m.replaceFirst("^ERROR: ", "").replaceFirst("^오류: ", "");
        String state = null; String name = null;
        for (Throwable t = root; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof java.sql.SQLException se && state == null) state = se.getSQLState();
        }
        if (name == null) { var mm = java.util.regex.Pattern.compile("\"([a-z0-9_]+)\"").matcher(m); if (mm.find() && (m.contains("constraint") || m.contains("제약"))) name = mm.group(1); }
        if (name != null && CONSTRAINT_MSG.containsKey(name)) return CONSTRAINT_MSG.get(name);
        boolean english = m.matches("(?s).*(violates|duplicate key|null value|constraint).*");
        boolean pgKorean = m.contains("제약 조건") || m.contains("중복된 키");
        if (!english && !pgKorean && !m.isBlank()) return m; // 트리거가 직접 만든 한글 안내
        if ("23505".equals(state) || m.contains("duplicate key") || m.contains("중복된 키")) return "이미 등록된 정보와 겹칩니다. 입력값을 확인하세요.";
        if ("23503".equals(state) || m.contains("foreign key")) return "연결된 데이터가 없거나, 다른 데이터가 사용 중이라 처리할 수 없습니다.";
        if ("23502".equals(state) || m.contains("null value")) return "필수 항목이 비어 있습니다.";
        if ("23514".equals(state) || m.contains("check constraint")) return "입력값이 허용 범위를 벗어났습니다. 입력값을 확인하세요.";
        return m.isBlank() ? "데이터 제약 위반" : "저장할 수 없습니다. 입력값을 확인하세요.";
    }

    // v12: SQLState 23514 (CHECK·트리거 업무 규칙 위반) → 409, 그 밖의 제약 위반 → 400
    static boolean is23514(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause())
            if (t instanceof java.sql.SQLException se && "23514".equals(se.getSQLState())) return true;
        return false;
    }

    // 26.10.20: DB CHECK·트리거(ERRCODE 23514 등) 위반 → 400 (v12: 23514 는 409), 트리거 메시지를 그대로 전달
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException e) {
        String m = friendly(e, e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(is23514(e) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST).body(Map.of("message", m));
    }

    // 트리거 예외가 JPA flush 시 JpaSystemException/TransactionSystemException 로 감싸져 올 때
    @ExceptionHandler({org.springframework.orm.jpa.JpaSystemException.class, org.springframework.transaction.TransactionSystemException.class})
    public ResponseEntity<Map<String, String>> handleJpaSystem(RuntimeException e) {
        Throwable t = e; while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        if (t instanceof java.sql.SQLException se && se.getSQLState() != null && se.getSQLState().startsWith("23")) {
            String m = friendly(e, se.getMessage());
            return ResponseEntity.status("23514".equals(se.getSQLState()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST).body(Map.of("message", m));
        }
        throw e;
    }

    // v12: JdbcTemplate 이 23514 를 DataIntegrity 가 아닌 다른 DataAccessException 으로 옮길 때도 409
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccess(org.springframework.dao.DataAccessException e) {
        if (!is23514(e)) throw e;
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", friendly(e, e.getMostSpecificCause().getMessage())));
    }
}
