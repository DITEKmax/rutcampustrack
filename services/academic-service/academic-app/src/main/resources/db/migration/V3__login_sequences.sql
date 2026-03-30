-- V3__login_sequences.sql
-- PostgreSQL sequences for atomic, race-condition-free login generation (per D-03/D-04).
-- V2 seed data contains only non-numeric logins: 'admin', 'teacher', 'student'.
-- No student00XXX or teacher00XXX logins exist; sequences safely start at 1.
CREATE SEQUENCE student_login_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE teacher_login_seq START WITH 1 INCREMENT BY 1;
