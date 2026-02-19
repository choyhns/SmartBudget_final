-- 저축 목표 제목 컬럼 추가
ALTER TABLE projectdb.saving_goals
ADD COLUMN IF NOT EXISTS goal_title varchar(128) NULL;

COMMENT ON COLUMN projectdb.saving_goals.goal_title IS '저축 목표 제목 (예: 내 집 마련, 여행 자금)';
