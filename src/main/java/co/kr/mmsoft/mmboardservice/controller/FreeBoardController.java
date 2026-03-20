package co.kr.mmsoft.mmboardservice.controller;

import co.kr.mmsoft.mmboardservice.domain.FreeBoard;
import co.kr.mmsoft.mmboardservice.mapper.FreeBoardMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Slf4j
@RestController
@RequestMapping("/api/freeboard")
@RequiredArgsConstructor
public class FreeBoardController {

    private final FreeBoardMapper freeBoardMapper;

    @Value("${app.upload.path:c:/mm-file/freeboard}")
    private String uploadPath;

    @Value("${app.upload.max-size-mb:1}")
    private int maxSizeMb;

    // ─── JWT에서 accountId 추출 (Spring Security 없이 수동 파싱) ─────
    private Long extractAccountId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            String token = authHeader.substring(7);
            String[] parts = token.split("\\.");
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = new ObjectMapper().readValue(payload, Map.class);
            Object sub = claims.get("sub");
            return sub != null ? Long.parseLong(sub.toString()) : null;
        } catch (Exception e) {
            log.warn("JWT 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    // ─── 역할 확인 (관리자 여부) ─────────────────────────────────────
    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        try {
            String token = authHeader.substring(7);
            String[] parts = token.split("\\.");
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = new ObjectMapper().readValue(payload, Map.class);
            Object roles = claims.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream().anyMatch(r ->
                    "ROLE_admin".equals(r) || "ROLE_super_admin".equals(r));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 목록 조회
     * - 비밀 게시글: 관리자이거나 본인 작성 글만 노출
     */
    @GetMapping
    public ResponseEntity<List<FreeBoard>> list(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "") String rolename,
            @RequestParam(defaultValue = "") String keyword) {

        Long myAccountId = extractAccountId(authHeader);
        boolean admin    = isAdmin(authHeader);

        List<FreeBoard> list = freeBoardMapper.findAll(rolename, keyword);

        // 비밀글 필터링: is_secret='Y' 이면 작성자·관리자만 내용 노출
        list.forEach(post -> {
            if ("Y".equals(post.getIsSecret())) {
                boolean canSee = admin ||
                    (myAccountId != null && myAccountId.equals(post.getAccountId()));
                if (!canSee) {
                    post.setTitle("비밀 게시글입니다.");
                    post.setContent("");
                    post.setUrl(null);
                }
            }
        });

        return ResponseEntity.ok(list);
    }

    /**
     * 단건 조회 + 조회수 증가
     */
    @GetMapping("/{id}")
    public ResponseEntity<FreeBoard> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        FreeBoard post = freeBoardMapper.findById(id);
        if (post == null) return ResponseEntity.notFound().build();

        Long myAccountId = extractAccountId(authHeader);
        boolean admin    = isAdmin(authHeader);

        if ("Y".equals(post.getIsSecret())) {
            boolean canSee = admin ||
                (myAccountId != null && myAccountId.equals(post.getAccountId()));
            if (!canSee) return ResponseEntity.status(403).build();
        }

        freeBoardMapper.incrementCnt(id);
        post.setCnt(post.getCnt() + 1);
        return ResponseEntity.ok(post);
    }

    /**
     * 원글 등록
     */
    @PostMapping
    public ResponseEntity<String> write(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody FreeBoard board) {

        Long accountId = extractAccountId(authHeader);
        board.setAccountId(accountId);

        // 원글: ref = maxRef + 1, step = 0, depth = 0
        int newRef = freeBoardMapper.maxRef() + 1;
        board.setRef(newRef);
        board.setStep(0);
        board.setDepth(0);

        if (board.getFreeboardRolename() == null || board.getFreeboardRolename().isBlank()) {
            board.setFreeboardRolename("일반");
        }

        int result = freeBoardMapper.insert(board);
        return result > 0
            ? ResponseEntity.ok("등록되었습니다.")
            : ResponseEntity.internalServerError().body("등록 실패");
    }

    /**
     * 댓글(답글) 등록
     */
    @PostMapping("/{parentId}/reply")
    public ResponseEntity<String> reply(
            @PathVariable Long parentId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody FreeBoard board) {

        FreeBoard parent = freeBoardMapper.findById(parentId);
        if (parent == null) return ResponseEntity.notFound().build();

        Long accountId = extractAccountId(authHeader);
        board.setAccountId(accountId);

        // 댓글: 부모와 같은 ref, step = 부모 step + 1, depth = 부모 depth + 1
        freeBoardMapper.shiftStep(parent.getRef(), parent.getStep());
        board.setRef(parent.getRef());
        board.setStep(parent.getStep() + 1);
        board.setDepth(parent.getDepth() + 1);
        board.setFreeboardRolename("일반");

        int result = freeBoardMapper.insert(board);
        return result > 0
            ? ResponseEntity.ok("댓글이 등록되었습니다.")
            : ResponseEntity.internalServerError().body("댓글 등록 실패");
    }

    /**
     * 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> update(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody FreeBoard board) {

        FreeBoard existing = freeBoardMapper.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        Long myAccountId = extractAccountId(authHeader);
        boolean admin    = isAdmin(authHeader);

        if (!admin && (myAccountId == null || !myAccountId.equals(existing.getAccountId()))) {
            return ResponseEntity.status(403).body("수정 권한이 없습니다.");
        }

        board.setFreeboardId(id);
        int result = freeBoardMapper.update(board);
        return result > 0
            ? ResponseEntity.ok("수정되었습니다.")
            : ResponseEntity.internalServerError().body("수정 실패");
    }

    /**
     * 삭제 (소프트 삭제)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        FreeBoard existing = freeBoardMapper.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        Long myAccountId = extractAccountId(authHeader);
        boolean admin    = isAdmin(authHeader);

        if (!admin && (myAccountId == null || !myAccountId.equals(existing.getAccountId()))) {
            return ResponseEntity.status(403).body("삭제 권한이 없습니다.");
        }

        int result = freeBoardMapper.delete(id);
        return result > 0
            ? ResponseEntity.ok("삭제되었습니다.")
            : ResponseEntity.internalServerError().body("삭제 실패");
    }

    /**
     * 파일 다운로드
     */
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadPath).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
        } catch (Exception e) {
            log.error("파일 다운로드 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 파일 업로드 (최대 1MB)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) return ResponseEntity.badRequest().body("파일을 선택해주세요.");

        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ResponseEntity.badRequest().body("파일 크기는 " + maxSizeMb + "MB 이하여야 합니다.");
        }

        try {
            File dir = new File(uploadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                log.error("업로드 디렉토리 생성 실패: {}", uploadPath);
                return ResponseEntity.internalServerError().body("업로드 디렉토리 생성 실패: " + uploadPath);
            }

            String originalName = file.getOriginalFilename();
            String savedName    = System.currentTimeMillis() + "_" + originalName;
            file.transferTo(new File(dir, savedName));

            return ResponseEntity.ok(Map.of(
                "fileName", savedName,
                "filePath", savedName
            ));
        } catch (Exception e) {
            log.error("파일 업로드 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("파일 업로드 실패");
        }
    }
}
