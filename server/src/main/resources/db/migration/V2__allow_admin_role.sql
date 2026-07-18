-- V2: users.role에 ADMIN 허용 (관리자 계정 도입 — 문제 콘텐츠 파이프라인 Phase 1)
alter table users drop constraint users_role_check;
alter table users add constraint users_role_check check (role in ('GUEST','MEMBER','ADMIN'));
