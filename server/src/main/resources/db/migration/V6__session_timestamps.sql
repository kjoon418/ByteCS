-- V6: 세션 시작·완료 시각 기록 (테스터 지표 수집)
--
-- 풀이 화면 최초 진입 시각(started_at)과 최초 완료 시각(completed_at)을 study_session에 더한다.
-- 관리자 지표(풀이 시작·세션 완료·완료 후 추가 학습 사용자 수)를 이 두 시각으로 집계한다.
-- 기존 행은 값이 없으므로 nullable로 추가한다(과거 세션의 시작/완료 시각은 소급 기록하지 않는다).
-- 컬럼 타입은 V1 baseline의 created_at(= Instant 매핑) 관례인 `timestamp(6) with time zone`을 따른다.
alter table study_session add column started_at timestamp(6) with time zone;
alter table study_session add column completed_at timestamp(6) with time zone;
