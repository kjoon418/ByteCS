-- V5: 추가 학습 폐지 및 하루 여러 세션 일원화 (D6·D9)
--
-- 하루 1세션 강제를 걷어낸다: (user_id, session_date) 유니크 제약을 제거해 같은 날 여러 세션을
-- 만들 수 있게 한다('조금 더 풀기'는 오늘 최신 세션이 완료됐을 때 새 세션을 시작한다).
-- 추가 학습(별도 무상태 학습 경로)은 세션으로 일원화되어 폐지되므로, 그 테이블을 통째로 제거한다.
-- 기존 추가 학습 데이터는 이관하지 않는다(원격·운영 배포 없음 — 로컬은 시드 재구성).
alter table study_session drop constraint uk_study_session_user_date;

-- 자식 테이블(FK 소유)을 먼저 지워야 부모(extra_study)를 지울 수 있다.
drop table extra_study_solved;
drop table extra_study;
