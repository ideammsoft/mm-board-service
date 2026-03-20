CREATE TABLE IF NOT EXISTS freeboard (
    freeboard_id       INT AUTO_INCREMENT PRIMARY KEY COMMENT '게시판 ID (자동증가)',
    account_id         INT                            COMMENT '작성자 회원번호 (FK, 비로그인 허용 시 NULL)',
    title              VARCHAR(255) NOT NULL           COMMENT '제목',
    content            TEXT         NOT NULL           COMMENT '내용',
    name               VARCHAR(50)                     COMMENT '작성자 이름',
    reg_date           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
    update_date        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    cnt                INT      DEFAULT 0              COMMENT '조회수',
    ref                INT      NOT NULL DEFAULT 0     COMMENT '글 그룹 (원글번호)',
    step               INT      NOT NULL DEFAULT 0     COMMENT '그룹 내 순서 (댓글 정렬)',
    depth              INT      NOT NULL DEFAULT 0     COMMENT '들여쓰기 단계 (0=원글, 1=댓글)',
    url                VARCHAR(255)                    COMMENT '첨부파일 경로',
    freeboard_rolename VARCHAR(50)  DEFAULT '일반'     COMMENT '게시판 유형 (공지/안내/일반/비밀)',
    is_deleted         CHAR(1)      DEFAULT 'N'        COMMENT '삭제 여부 (Y/N)',
    CONSTRAINT FK_freeboard_account FOREIGN KEY (account_id)
        REFERENCES account (account_id) ON DELETE SET NULL
) COMMENT '커뮤니티 자유게시판';
