-- Auth owns credential naming reservations. Existing public name+four-digit and
-- collision suffix behavior is retained without an Academic login query.
CREATE SEQUENCE teacher_login_seq START WITH 1 INCREMENT BY 1;
