-- V7: 선호 난이도 설정 (난이도 조절 1차 — 선호 난이도 선택 + 가중 출제)
--
-- 사용자가 선호하는 문제 난이도를 저장한다. NULL = 미설정(현행 균등 무작위 배정 유지 — 강제 없음).
-- 값은 Difficulty enum 이름(EASY/MEDIUM/HARD)이다. 컬럼 타입·체크 제약은 V1 baseline의 problem.difficulty
-- (같은 Difficulty enum, `varchar(255)` + in-list 체크) 관례를 그대로 따른다 —
-- ddl-auto=validate가 Hibernate의 @Enumerated(EnumType.STRING) 기본 매핑(varchar(255))과 일치해야 한다.
-- (계획 §4.1은 VARCHAR(16)을 예시했으나, validate 정합·기존 컬럼 일관성을 위해 255로 확정.)
alter table users add column preferred_difficulty varchar(255)
    constraint users_preferred_difficulty_check check (preferred_difficulty in ('EASY','MEDIUM','HARD'));

-- 완료 화면 난이도 제안에 응답(선택 또는 거절)했는지. 설정 화면에서 선호를 직접 지정한 경우에도 TRUE로 올린다
-- (이미 아는 사용자에게 다시 제안하지 않는다). 기존 행은 아직 응답한 적 없으므로 기본값 FALSE로 채운다.
alter table users add column difficulty_prompt_done boolean not null default false;
