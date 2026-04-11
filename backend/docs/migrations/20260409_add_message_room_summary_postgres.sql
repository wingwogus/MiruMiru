-- Adds read-optimized room summary storage for chat room list queries.
-- Intended for prod/stage PostgreSQL rollout where ddl-auto is disabled.

CREATE TABLE IF NOT EXISTS message_room_summary (
    room_id BIGINT PRIMARY KEY REFERENCES message_room(id),
    last_message_id BIGINT NULL,
    last_message_content TEXT NULL,
    last_message_created_at TIMESTAMP NULL,
    member_1_unread_count BIGINT NOT NULL DEFAULT 0,
    member_2_unread_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_summary_last_message_id
    ON message_room_summary (last_message_id);

CREATE INDEX IF NOT EXISTS idx_room_summary_last_message_created_at
    ON message_room_summary (last_message_created_at);

-- One-time backfill for existing rooms.
INSERT INTO message_room_summary (
    room_id,
    last_message_id,
    last_message_content,
    last_message_created_at,
    member_1_unread_count,
    member_2_unread_count,
    created_at,
    updated_at
)
SELECT
    mr.id,
    lm.id AS last_message_id,
    lm.content AS last_message_content,
    lm.created_at AS last_message_created_at,
    (
        SELECT COUNT(*)
        FROM message m1
        WHERE m1.room_id = mr.id
          AND m1.sender_id <> mr.member_id_1
          AND m1.id > COALESCE(mr.member_1_last_read_id, 0)
    ) AS member_1_unread_count,
    (
        SELECT COUNT(*)
        FROM message m2
        WHERE m2.room_id = mr.id
          AND m2.sender_id <> mr.member_id_2
          AND m2.id > COALESCE(mr.member_2_last_read_id, 0)
    ) AS member_2_unread_count,
    NOW(),
    NOW()
FROM message_room mr
LEFT JOIN LATERAL (
    SELECT m.id, m.content, m.created_at
    FROM message m
    WHERE m.room_id = mr.id
    ORDER BY m.id DESC
    LIMIT 1
) lm ON TRUE
ON CONFLICT (room_id) DO NOTHING;
