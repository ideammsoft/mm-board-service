-- 비밀글 여부 컬럼 추가 (기존 '비밀' rolename 데이터는 is_secret='Y'로 마이그레이션)
ALTER TABLE freeboard
    ADD COLUMN is_secret CHAR(1) DEFAULT 'N' COMMENT '비밀글 여부 (Y/N)';

-- 기존 freeboardRolename='비밀'인 데이터를 is_secret='Y', rolename='일반'으로 전환
UPDATE freeboard
SET is_secret          = 'Y',
    freeboard_rolename = '일반'
WHERE freeboard_rolename = '비밀'
  AND is_deleted = 'N';
