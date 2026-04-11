# Chat Room List Bottleneck Mitigation Strategy

## Why Current Structure Is Slow
- `getMyRooms` currently computes unread count by joining the entire `message` table and aggregating with `SUM` per room.
- The read query uses `JOIN + GROUP BY + ORDER BY` over chat message rows, so concurrent requests multiply DB CPU and sort/aggregation pressure.
- Last-message lookup is coupled to message-table aggregation instead of a precomputed read model.
- Result: tail latency spikes under concurrent list refresh (`p95/p99` grows sharply).

## Why the New Structure Is Faster
- Move chat room list to a summary read model (`message_room_summary`) so list reads become point-lookup joins on room + summary.
- Keep summary synchronized on write path (room create / message send / mark read), so reads avoid runtime aggregation.
- Query complexity becomes proportional to number of rooms returned, not message volume.
- This directly removes the heavy `message` join/group/sum from `getMyRooms`.

## Data Model Changes
### New Table / Entity
- `message_room_summary`
  - `room_id` (PK, FK -> `message_room.id`)
  - `last_message_id`
  - `last_message_content`
  - `last_message_created_at`
  - `member_1_unread_count`
  - `member_2_unread_count`
  - `created_at`, `updated_at`

### Changed Query
- `ChatRoomReadRepositoryImpl.findMyRooms`
  - Before: join `message`, aggregate unread with `SUM`, group by room-level columns
  - After: join `message_room_summary` and read precomputed unread/last message fields

### Write-Path Sync
- On room create: ensure summary row exists.
- On message send: update last message fields + receiver unread count.
- On mark read: recompute reader unread from read pointer and store.
- Add reconciliation helper to recompute from read pointers for drift recovery.

## Required Indexes
- `message_room_summary(room_id)` via PK
- `idx_room_summary_last_message_id`
- `idx_room_summary_last_message_created_at`
- Existing `message(room_id, id)` index remains critical for reconciliation and read-pointer based unread recompute.

## Redis Extension Design (Phase 2)
### Key Strategy
- `rooms:{userId}:v1`
- Value: serialized room-summary list payload (or compact per-room map) for that user.
- Optional sharding per page/limit:
  - `rooms:{userId}:v1:limit:{N}`

### Partial Update Events
- `ROOM_CREATED` (participants: user A, user B)
- `MESSAGE_SENT` (room, sender, receiver, last message fields, receiver unread)
- `READ_MARKED` (room, reader, unread after read)
- Cache updater applies event only to affected users and room entries.

### Cache Miss + TTL
- Read flow:
  1. cache hit -> return
  2. miss -> query DB summary model -> fill cache -> return
- TTL:
  - short-to-mid TTL (for example 30~120s) plus event-driven invalidation/update
  - jitter to avoid synchronized expiry stampede

### Consistency Risks and Mitigations
- Event duplication:
  - use idempotency key (`eventId`) and de-dup window.
- Out-of-order delivery:
  - apply only if incoming `lastMessageId` is newer than cached one.
- Drift in unread counters:
  - unread is recoverable from read pointers + message ids; do periodic reconciliation.
- Missed invalidation:
  - fallback TTL expiration + scheduled reconciliation batch.

## Reconciliation Batch (Drift Safety Net)
- Periodic batch scans active/recent rooms.
- Recompute:
  - latest message fields
  - unread counts for both participants from read pointers
- Overwrite summary + optionally republish cache-refresh event.

## Testing and Verification Strategy
- Unit tests:
  - summary sync service (message send, mark read, reconciliation).
- Integration tests:
  - room list reflects unread/last-message updates after send/read.
  - no regression in create/send/read API behavior.
- Query behavior:
  - verify `getMyRooms` no longer depends on message aggregation query path.
- Performance validation:
  - compare baseline vs new path under same concurrency profile.

## Gradual Rollout Plan
1. Deploy schema + summary write sync (dual-write) while keeping old read query behind flag.
2. Backfill existing rooms once.
3. Switch `getMyRooms` read path to summary model behind feature flag.
4. Observe latency/error metrics; roll back by toggling read flag if needed.
5. Introduce Redis cache layer (read-through + event updates) after summary model stabilizes.
