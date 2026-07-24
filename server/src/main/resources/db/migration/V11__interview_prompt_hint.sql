-- V11: 면접 질문 점진 공개 힌트 (면접 힌트 — 문제 힌트 메커니즘 미러)
--
-- 면접 질문마다 약→강 순의 힌트(0~N개, 선택)를 붙인다. 힌트 열람은 채점·준비도·쿼터에 영향을 주지 않는다(무낙인).
-- 힌트는 텍스트만 다룬다(면접 질문은 코드 스니펫 개념이 없다) — problem_hint와 달리 hint_code_snippet이 없다.
create table interview_prompt_hint (
    hint_index integer not null,
    prompt_id bigint not null,
    hint_text text not null,
    primary key (hint_index, prompt_id)
);

alter table interview_prompt_hint
    add constraint fk_interview_prompt_hint_prompt
    foreign key (prompt_id)
    references interview_prompt;

-- 이 칸에서 공개한 힌트 수(약→강 앞에서부터). 기존 행·기존 시드와의 하위 호환을 위해 기본값 0.
alter table interview_session_item
    add column revealed_hint_count integer not null default 0;
