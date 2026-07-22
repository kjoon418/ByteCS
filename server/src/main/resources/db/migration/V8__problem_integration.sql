-- V8: 연결 문제 명시 속성 (DI12 — 게이트 판별을 '개념 2개 이상'에서 큐레이터 지정 플래그로 변경)
--
-- 연결 문제 여부를 문제에 명시 속성으로 둔다. TRUE인 문제만 새 개념 배정의 하드 게이트·잠금 해제 연출 대상이다.
-- 기존 행은 지정된 적이 없으므로 기본값 FALSE로 채운다(하위 호환 — 다개념 태깅만으로는 잠기지 않는다).
-- 컬럼 타입은 V1 baseline의 not-null boolean 관례(예: session_item.misconception_hint_seen)를 따른다 —
-- ddl-auto=validate가 Hibernate의 Boolean(nullable=false) 매핑과 일치해야 한다.
alter table problem add column is_integration boolean not null default false;
