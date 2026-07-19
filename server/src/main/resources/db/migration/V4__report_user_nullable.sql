-- V4: 계정 삭제 시 신고 데이터 익명화 보존 (D10)
--
-- 신고(content_report)는 콘텐츠 품질 운영 데이터라, 신고자 계정이 삭제돼도 신고 자체는 지우지 않고
-- user_id만 null로 지워 보존한다(익명화). 그러려면 user_id가 nullable이어야 한다.
alter table content_report alter column user_id drop not null;
