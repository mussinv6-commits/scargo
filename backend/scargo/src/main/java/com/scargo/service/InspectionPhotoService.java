package com.scargo.service;

import com.scargo.entity.Account;
import com.scargo.entity.Account.UserType;
import com.scargo.entity.ContainerInspection;
import com.scargo.repository.ContainerInspectionRepository;
import com.scargo.security.ContainerInspectionAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 26.10.24 컨테이너 점검표 손상 사진 첨부 (표: container_inspection_photos, DB/추가_20261024_기능4종.sql)
 *  - 조회: 점검 기록을 볼 수 있는 사람 (ContainerInspectionAccessPolicy.requireView)
 *  - 올리기 · 삭제: 점검표를 수정할 수 있는 사람 + 작성 중(DRAFT)인 기록만 (확정본은 사진도 바꿀 수 없음)
 *  - jpg · png · webp, 사진 1장 5MB 이하, 기록당 10장 이하. 파일은 scargo.upload-dir/inspections/{id}/ 에 저장
 */
@Service
@RequiredArgsConstructor
public class InspectionPhotoService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_PHOTOS = 10;

    private final ContainerInspectionRepository inspectionRepository;
    private final ContainerInspectionAccessPolicy policy;
    private final JdbcTemplate jdbc;

    @Value("${scargo.upload-dir:./uploads}")
    private String uploadDir;

    public record PhotoFile(byte[] bytes, String contentType, String fileName) { }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Authentication authentication, Long inspectionId) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = find(inspectionId);
        policy.requireView(actor, c);
        return jdbc.queryForList("SELECT photo_id AS \"photoId\", inspection_id AS \"inspectionId\", file_name AS \"fileName\", "
                + "content_type AS \"contentType\", size_bytes AS \"sizeBytes\", created_at AS \"createdAt\" "
                + "FROM container_inspection_photos WHERE inspection_id = ? ORDER BY photo_id", inspectionId);
    }

    @Transactional
    public Map<String, Object> upload(Authentication authentication, Long inspectionId, MultipartFile file) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = find(inspectionId);
        requireEdit(actor, c);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("사진 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("사진은 1장당 5MB 이하만 올릴 수 있습니다.");
        }
        String ext = extensionOf(file.getContentType());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM container_inspection_photos WHERE inspection_id = ?",
                Integer.class, inspectionId);
        if (count != null && count >= MAX_PHOTOS) {
            throw new IllegalStateException("사진은 점검 기록당 " + MAX_PHOTOS + "장까지 올릴 수 있습니다.");
        }
        String stored = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(uploadDir, "inspections", String.valueOf(inspectionId)).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(stored));
        } catch (IOException e) {
            throw new IllegalStateException("사진을 저장하지 못했습니다: " + e.getMessage());
        }
        String original = file.getOriginalFilename() == null ? stored : file.getOriginalFilename();
        if (original.length() > 200) {
            original = original.substring(original.length() - 200);
        }
        Long photoId = jdbc.queryForObject(
                "INSERT INTO container_inspection_photos (inspection_id, file_name, stored_name, content_type, size_bytes, uploaded_by_account_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?) RETURNING photo_id",
                Long.class, inspectionId, original, stored, file.getContentType(), file.getSize(), actor.getAccountId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("photoId", photoId);
        result.put("inspectionId", inspectionId);
        result.put("fileName", original);
        result.put("contentType", file.getContentType());
        result.put("sizeBytes", file.getSize());
        return result;
    }

    @Transactional(readOnly = true)
    public PhotoFile file(Authentication authentication, Long inspectionId, Long photoId) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = find(inspectionId);
        policy.requireView(actor, c);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT stored_name, content_type, file_name FROM container_inspection_photos WHERE photo_id = ? AND inspection_id = ?",
                photoId, inspectionId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("사진을 찾을 수 없습니다: " + photoId);
        }
        Map<String, Object> row = rows.get(0);
        Path path = Paths.get(uploadDir, "inspections", String.valueOf(inspectionId), String.valueOf(row.get("stored_name")))
                .toAbsolutePath().normalize();
        try {
            return new PhotoFile(Files.readAllBytes(path), String.valueOf(row.get("content_type")), String.valueOf(row.get("file_name")));
        } catch (IOException e) {
            throw new IllegalArgumentException("사진 파일이 저장소에 없습니다: " + photoId);
        }
    }

    @Transactional
    public void delete(Authentication authentication, Long inspectionId, Long photoId) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = find(inspectionId);
        requireEdit(actor, c);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT stored_name FROM container_inspection_photos WHERE photo_id = ? AND inspection_id = ?", photoId, inspectionId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("사진을 찾을 수 없습니다: " + photoId);
        }
        jdbc.update("DELETE FROM container_inspection_photos WHERE photo_id = ?", photoId);
        try {
            Files.deleteIfExists(Paths.get(uploadDir, "inspections", String.valueOf(inspectionId),
                    String.valueOf(rows.get(0).get("stored_name"))).toAbsolutePath().normalize());
        } catch (IOException ignored) {
            // 파일이 남아도 기록은 지워짐 (다시 보이지 않음)
        }
    }

    // ContainerInspectionService.requireEdit 과 같은 규칙: 조회 범위 → 역할 · 허브 업무 → 작성 중(DRAFT)
    private void requireEdit(Account actor, ContainerInspection c) {
        policy.requireView(actor, c);
        UserType type = actor.getUserType();
        if (type == UserType.HUB_OP) {
            policy.requireHubWork(actor, c.getOrderId());
        } else if (type != UserType.ADMIN && type != UserType.CORPORATE_APPROVED && type != UserType.CONTAINER_DRIVER) {
            throw new AccessDeniedException("점검 기록에 사진을 올릴 권한이 없습니다.");
        }
        if (!c.isDraft()) {
            throw new IllegalStateException("작성 중(DRAFT)인 점검 기록만 사진을 바꿀 수 있습니다 (현재 " + c.getRecordStatus() + ")");
        }
    }

    private ContainerInspection find(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "점검 기록이 없습니다: " + id));
    }

    private static String extensionOf(String contentType) {
        if ("image/jpeg".equals(contentType)) return "jpg";
        if ("image/png".equals(contentType)) return "png";
        if ("image/webp".equals(contentType)) return "webp";
        throw new IllegalArgumentException("jpg · png · webp 사진만 올릴 수 있습니다.");
    }
}
