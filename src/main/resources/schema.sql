CREATE TABLE IF NOT EXISTS urls (

    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,

    short_code   VARCHAR(32)   COLLATE utf8mb4_bin NULL,


    original_url VARCHAR(2048) NOT NULL,

    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_urls_short_code UNIQUE (short_code)
)

AUTO_INCREMENT = 1000000;