-- WHAT Schedule Bot D1 Database Schema

-- Chat / Group configurations
CREATE TABLE IF NOT EXISTS chats (
    chat_id TEXT PRIMARY KEY,
    chat_type TEXT NOT NULL, -- 'private' | 'group' | 'supergroup'
    title TEXT,
    institution TEXT NOT NULL DEFAULT 'rksi',
    target_type TEXT NOT NULL DEFAULT 'group', -- 'group' | 'teacher'
    group_name TEXT,
    group_id TEXT,
    pending_action TEXT,
    notify_replacements INTEGER NOT NULL DEFAULT 1,
    notify_tomorrow_time TEXT DEFAULT '19:00',
    last_replacements_hash TEXT,
    last_daily_sent_date TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chats_group ON chats(institution, group_name);
CREATE INDEX IF NOT EXISTS idx_chats_notify ON chats(notify_replacements);

-- Delivery Survey State
CREATE TABLE IF NOT EXISTS survey_sessions (
    id TEXT PRIMARY KEY,
    chat_id TEXT NOT NULL,
    survey_id TEXT NOT NULL,
    current_question_index INTEGER NOT NULL DEFAULT 0,
    answers_json TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'in_progress', -- 'in_progress' | 'completed'
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_survey_sessions_chat ON survey_sessions(chat_id, survey_id);

-- Delivered notifications tracking
CREATE TABLE IF NOT EXISTS seen_notifications (
    id TEXT PRIMARY KEY,
    notification_id TEXT NOT NULL,
    chat_id TEXT NOT NULL,
    seen_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_seen_notif ON seen_notifications(chat_id, notification_id);
