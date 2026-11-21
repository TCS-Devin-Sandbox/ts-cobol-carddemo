CREATE TABLE user_security (
 user_id CHAR(8) PRIMARY KEY,
 first_name CHAR(20) NOT NULL,
 last_name CHAR(20) NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 user_type CHAR(1) NOT NULL CHECK (user_type IN ('A','U')),
 filler CHAR(23) NOT NULL
);
