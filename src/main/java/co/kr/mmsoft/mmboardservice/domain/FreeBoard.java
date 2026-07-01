package co.kr.mmsoft.mmboardservice.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FreeBoard {
    private Long          freeboardId;       // PK
    private Long          accountId;         // 작성자 회원번호
    private String        title;             // 제목
    private String        content;           // 내용
    private String        name;              // 작성자 이름
    private LocalDateTime regDate;           // 작성일
    private LocalDateTime updateDate;        // 수정일
    private int           cnt;               // 조회수
    private int           ref;               // 글 그룹 (원글번호)
    private int           step;              // 그룹 내 순서
    private int           depth;             // 들여쓰기 단계
    private String        url;               // 첨부파일 경로
    private String        freeboardRolename; // 게시판 유형 (공지/안내/일반)
    private String        isSecret;          // 비밀글 여부 (Y/N)
    private String        isDeleted;         // 삭제 여부 (Y/N)
    private boolean       locked;            // 비밀글이면서 열람 불가(제목만 노출) 여부 - 응답 전용
}
