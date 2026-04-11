CREATE TABLE IF NOT EXISTS chat_block (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_1_id BIGINT NOT NULL,
    member_2_id BIGINT NOT NULL,
    blocked_by_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_block_member_pair_by_owner UNIQUE (member_1_id, member_2_id, blocked_by_id),
    CONSTRAINT fk_chat_block_member_1 FOREIGN KEY (member_1_id) REFERENCES member (id),
    CONSTRAINT fk_chat_block_member_2 FOREIGN KEY (member_2_id) REFERENCES member (id),
    CONSTRAINT fk_chat_block_blocked_by FOREIGN KEY (blocked_by_id) REFERENCES member (id),
    INDEX idx_chat_block_member1 (member_1_id),
    INDEX idx_chat_block_member2 (member_2_id)
);

SET @chat_block_pair_unique_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_block'
      AND index_name = 'uk_chat_block_member_pair'
);

SET @chat_block_pair_by_owner_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_block'
      AND index_name = 'uk_chat_block_member_pair_by_owner'
);

SET @chat_block_pair_migration_sql := IF(
    @chat_block_pair_unique_exists > 0 AND @chat_block_pair_by_owner_exists = 0,
    'ALTER TABLE chat_block DROP INDEX uk_chat_block_member_pair, ADD CONSTRAINT uk_chat_block_member_pair_by_owner UNIQUE (member_1_id, member_2_id, blocked_by_id)',
    'SELECT 1'
);
PREPARE chat_block_pair_stmt FROM @chat_block_pair_migration_sql;
EXECUTE chat_block_pair_stmt;
DEALLOCATE PREPARE chat_block_pair_stmt;

CREATE TABLE IF NOT EXISTS chat_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    room_id BIGINT NULL,
    message_id BIGINT NULL,
    reason VARCHAR(100) NOT NULL,
    detail TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id),
    CONSTRAINT fk_chat_report_target FOREIGN KEY (target_id) REFERENCES member (id),
    INDEX idx_chat_report_reporter_created (reporter_id, created_at),
    INDEX idx_chat_report_target_created (target_id, created_at)
);

CREATE TABLE IF NOT EXISTS message_room_summary (
    room_id BIGINT NOT NULL,
    last_message_id BIGINT NULL,
    last_message_content TEXT NULL,
    last_message_created_at DATETIME(6) NULL,
    member_1_unread_count BIGINT NOT NULL DEFAULT 0,
    member_2_unread_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (room_id),
    CONSTRAINT fk_room_summary_room FOREIGN KEY (room_id) REFERENCES message_room (id),
    INDEX idx_room_summary_last_message_id (last_message_id),
    INDEX idx_room_summary_last_message_created_at (last_message_created_at)
);
