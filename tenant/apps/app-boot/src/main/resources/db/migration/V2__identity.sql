CREATE TABLE users (
    id            uuid          PRIMARY KEY,
    email         varchar(320)  NOT NULL,
    display_name  varchar(200)  NOT NULL,
    password_hash varchar(100)  NOT NULL,
    role          varchar(20)   NOT NULL,
    status        varchar(20)   NOT NULL,
    created_at    timestamptz   NOT NULL
);

CREATE UNIQUE INDEX users_email_key ON users (lower(email));

CREATE TABLE user_sessions (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX user_sessions_token_key ON user_sessions (token_hash);
CREATE INDEX user_sessions_user_idx ON user_sessions (user_id);
