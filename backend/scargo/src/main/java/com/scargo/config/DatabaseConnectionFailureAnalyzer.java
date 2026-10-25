package com.scargo.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.env.Environment;

import java.sql.SQLException;

/**
 * 26.10.24 DB 접속 실패 원인 안내.
 * 기동 중 PostgreSQL 접속이 실패하면 스택 트레이스 대신 원인과 설정 방법을 "APPLICATION FAILED TO START" 에 보여 준다.
 *  - 비밀번호 미설정: "The server requested SCRAM-based authentication, but no password was provided."
 *    (application.yml 의 spring.datasource.password = ${SCARGO_DB_PASSWORD:} 가 비어 있을 때)
 *  - 비밀번호 틀림(28P01), DB 없음(3D000), 서버 접속 불가(08001)
 * 비밀번호 값은 출력하지 않는다 (설정 여부만). 등록: META-INF/spring.factories
 */
public class DatabaseConnectionFailureAnalyzer extends AbstractFailureAnalyzer<SQLException> {

    private static final String HOW_TO_SET = """
            DB 접속 정보는 아래 중 하나로 지정하세요 (비밀번호를 application.yml 에 직접 적지 마세요).
              1) 환경변수: SCARGO_DB_URL, SCARGO_DB_USERNAME, SCARGO_DB_PASSWORD
                 - Eclipse/STS: Run Configurations → Spring Boot App → (실행 설정) → Environment 탭에 추가
                 - PowerShell: $env:SCARGO_DB_PASSWORD = "<비밀번호>" 후 같은 창에서 gradlew bootRun
              2) 실행 폴더(backend/scargo)에 application-local.yml 을 만들기 (application-local.yml.example 복사, 공유 금지)
            자세한 방법: README.md 6장 · 11장""";

    private final Environment environment;

    public DatabaseConnectionFailureAnalyzer(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SQLException cause) {
        String message = String.valueOf(cause.getMessage());
        String state = cause.getSQLState() == null ? "" : cause.getSQLState();
        String target = target();

        if (message.contains("no password was provided")) {
            return new FailureAnalysis(
                    "PostgreSQL 이 비밀번호 인증(SCRAM)을 요구하지만 백엔드에 DB 비밀번호가 설정되지 않았습니다. " + target,
                    HOW_TO_SET, cause);
        }
        if ("28P01".equals(state) || message.contains("password authentication failed")) {
            return new FailureAnalysis(
                    "PostgreSQL 비밀번호 인증에 실패했습니다 (사용자 이름 또는 비밀번호가 틀림). " + target,
                    "SCARGO_DB_USERNAME / SCARGO_DB_PASSWORD 값을 확인하세요.\n" + HOW_TO_SET, cause);
        }
        if ("3D000".equals(state)) {
            return new FailureAnalysis(
                    "접속하려는 데이터베이스가 없습니다. " + target,
                    "새 DB 를 만들고 DB/SCARGO_Integrated_Full_Install_20261024.sql 을 실행하거나(README 3 · 4장), SCARGO_DB_URL 의 DB 이름을 확인하세요.",
                    cause);
        }
        if (state.startsWith("08")) {
            return new FailureAnalysis(
                    "PostgreSQL 서버에 연결할 수 없습니다 (서버 미실행 · 호스트 · 포트 확인). " + target,
                    "PostgreSQL 서비스가 실행 중인지, SCARGO_DB_URL 의 호스트 · 포트가 맞는지 확인하세요.", cause);
        }
        return null; // 그 밖의 SQL 오류는 기본 처리
    }

    // 접속 대상 요약 (비밀번호는 설정 여부만)
    private String target() {
        String url = environment.getProperty("spring.datasource.url", "(없음)");
        String user = environment.getProperty("spring.datasource.username", "(없음)");
        String pw = environment.getProperty("spring.datasource.password", "");
        return "[url=" + url + ", username=" + user + ", password=" + (pw.isEmpty() ? "설정 안 됨" : "설정됨") + "]";
    }
}
