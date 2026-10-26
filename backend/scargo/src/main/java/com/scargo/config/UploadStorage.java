package com.scargo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * fix_1300: 업로드 저장 위치 단일화 + 확장자 화이트리스트 + /uploads/** 제공.
 *  - 저장 루트는 scargo.upload-root 하나 (하위: app/handover, returns, return-attachments, inspections, posts)
 *  - DB 에는 "/uploads/하위/파일명" 으로 저장, 같은 경로를 GET /uploads/** 로 제공 (SecurityConfig 에서 로그인 필요)
 *  - 허용: jpg·jpeg·png·pdf, Content-Type 도 image/jpeg·image/png·application/pdf 여야 함
 */
@Configuration
public class UploadStorage implements WebMvcConfigurer {
    private static Path root = Path.of("./uploads").toAbsolutePath().normalize();

    private static final Map<String, String> ALLOWED = Map.of(
            ".jpg", "image/jpeg", ".jpeg", "image/jpeg", ".png", "image/png", ".pdf", "application/pdf");

    public UploadStorage(@Value("${scargo.upload-root:./uploads}") String dir) {
        root = Path.of(dir).toAbsolutePath().normalize();
        // fix_1400: 기동 시 업로드 루트 생성 (없으면 root.toUri() 끝에 '/' 가 안 붙어 첫 실행 때 /uploads/** 가 전부 404)
        try { Files.createDirectories(root); } catch (IOException e) { throw new IllegalStateException("업로드 폴더를 만들 수 없습니다: " + root, e); }
    }

    public static Path root() { return root; }

    public static Path dir(String sub) throws IOException {
        Path d = root.resolve(sub).normalize();
        Files.createDirectories(d);
        return d;
    }

    /** 확장자(소문자, 점 포함) 반환. 허용 목록 밖이거나 Content-Type 이 맞지 않으면 IllegalArgumentException(400) */
    public static String checkedExt(MultipartFile f) {
        if (f == null || f.isEmpty()) throw new IllegalArgumentException("파일을 올려 주세요.");
        String n = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
        String ext = n.contains(".") ? n.substring(n.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        String ct = f.getContentType() == null ? "" : f.getContentType().toLowerCase(Locale.ROOT);
        // 앱 서명 캔버스는 파일명 없이 blob(image/png) 으로 올라오는 경우가 있어 Content-Type 으로 확장자를 정함
        if (ext.isEmpty()) ext = ALLOWED.entrySet().stream().filter(e -> e.getValue().equals(ct)).map(Map.Entry::getKey).findFirst().orElse("");
        String want = ALLOWED.get(ext);
        if (want == null) throw new IllegalArgumentException("jpg·png·pdf 파일만 올릴 수 있습니다.");
        if (!want.equals(ct)) throw new IllegalArgumentException("파일 형식이 확장자와 맞지 않습니다 (" + ct + ").");
        return ext.equals(".jpeg") ? ".jpg" : ext;
    }

    /** 저장 후 공개 경로("/uploads/sub/name") 반환 */
    public static String save(String sub, String baseName, MultipartFile f) throws IOException {
        String ext = checkedExt(f);
        String name = baseName + ext;
        f.transferTo(dir(sub).resolve(name).toFile());
        return "/uploads/" + sub + "/" + name;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry r) {
        r.addResourceHandler("/uploads/**").addResourceLocations(location());
    }

    /** 리소스 위치는 반드시 '/' 로 끝나야 하위 파일을 찾는다 */
    static String location() {
        String u = root.toUri().toString();
        return u.endsWith("/") ? u : u + "/";
    }
}
