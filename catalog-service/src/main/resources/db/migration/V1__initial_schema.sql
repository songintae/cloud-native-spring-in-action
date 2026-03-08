CREATE TABLE book
(
    id               BIGSERIAL PRIMARY KEY NOT NULL,
    isbn             varchar(255) UNIQUE   NOT NULL,
    title            varchar(255)          NOT NULL,
    author           varchar(255)          NOT NULL,
    price            float8                NOT NULL,
    created_at       timestamp             NOT NULL,
    last_modified_at timestamp             NOT NULL,
    version          int                   NOT NULL
);