import { ChatConfig } from '../types';

export class DatabaseService {
  constructor(private db: D1Database) {}

  async getChat(chatId: string | number): Promise<ChatConfig | null> {
    const id = String(chatId);
    return await this.db.prepare(`SELECT * FROM chats WHERE chat_id = ?`).bind(id).first<ChatConfig>();
  }

  async upsertChat(config: Partial<ChatConfig> & { chat_id: string | number; chat_type?: string }): Promise<void> {
    const id = String(config.chat_id);
    const now = new Date().toISOString();
    const existing = await this.getChat(id);

    if (existing) {
      await this.db.prepare(`
        UPDATE chats 
        SET group_name = COALESCE(?, group_name),
            group_id = COALESCE(?, group_id),
            institution = COALESCE(?, institution),
            target_type = COALESCE(?, target_type),
            title = COALESCE(?, title),
            notify_replacements = COALESCE(?, notify_replacements),
            notify_tomorrow_time = COALESCE(?, notify_tomorrow_time),
            pending_action = ?,
            updated_at = ?
        WHERE chat_id = ?
      `).bind(
        config.group_name ?? null,
        config.group_id ?? null,
        config.institution ?? null,
        config.target_type ?? null,
        config.title ?? null,
        config.notify_replacements ?? null,
        config.notify_tomorrow_time ?? null,
        config.pending_action ?? null,
        now,
        id
      ).run();
    } else {
      await this.db.prepare(`
        INSERT INTO chats (chat_id, chat_type, title, institution, target_type, group_name, group_id, notify_replacements, notify_tomorrow_time, pending_action, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).bind(
        id,
        config.chat_type || 'private',
        config.title || null,
        config.institution || 'rksi',
        config.target_type || 'group',
        config.group_name || null,
        config.group_id || null,
        config.notify_replacements ?? 1,
        config.notify_tomorrow_time || '19:00',
        config.pending_action || null,
        now,
        now
      ).run();
    }
  }

  async setPendingAction(chatId: string | number, action: string | null): Promise<void> {
    const id = String(chatId);
    await this.db.prepare(`UPDATE chats SET pending_action = ?, updated_at = ? WHERE chat_id = ?`)
      .bind(action, new Date().toISOString(), id).run();
  }

  async setChatTarget(chatId: string | number, institution: string, targetType: 'group' | 'teacher', targetName: string, targetId?: string): Promise<void> {
    const id = String(chatId);
    const now = new Date().toISOString();
    await this.db.prepare(`
      UPDATE chats 
      SET institution = ?, target_type = ?, group_name = ?, group_id = ?, pending_action = NULL, updated_at = ?
      WHERE chat_id = ?
    `).bind(institution, targetType, targetName, targetId || targetName, now, id).run();
  }

  async setChatGroup(chatId: string | number, institution: string, groupName: string, groupId?: string): Promise<void> {
    await this.setChatTarget(chatId, institution, 'group', groupName, groupId);
  }

  async getSubscribedChatsForReplacements(institution?: string): Promise<ChatConfig[]> {
    if (institution) {
      const res = await this.db.prepare(`
        SELECT * FROM chats 
        WHERE notify_replacements = 1 AND institution = ? AND group_name IS NOT NULL
      `).bind(institution).all<ChatConfig>();
      return res.results || [];
    }
    const res = await this.db.prepare(`
      SELECT * FROM chats 
      WHERE notify_replacements = 1 AND group_name IS NOT NULL
    `).all<ChatConfig>();
    return res.results || [];
  }

  async getChatsForDailySchedule(tag: string): Promise<ChatConfig[]> {
    const res = await this.db.prepare(`
      SELECT * FROM chats 
      WHERE group_name IS NOT NULL 
        AND notify_tomorrow_time != 'off'
        AND (last_daily_sent_date IS NULL OR instr(last_daily_sent_date, ?) = 0)
    `).bind(tag).all<ChatConfig>();
    return res.results || [];
  }

  async markDailySent(chatId: string | number, tag: string, existingSentDate?: string): Promise<void> {
    const id = String(chatId);
    let newSentDate = tag;
    const datePrefix = tag.split('_')[0];
    if (existingSentDate && existingSentDate.includes(datePrefix)) {
      newSentDate = `${existingSentDate},${tag}`;
    }
    await this.db.prepare(`
      UPDATE chats SET last_daily_sent_date = ?, updated_at = ? WHERE chat_id = ?
    `).bind(newSentDate, new Date().toISOString(), id).run();
  }

  async setDailySentDate(chatId: string | number, dateStr: string): Promise<void> {
    await this.markDailySent(chatId, dateStr);
  }

  async updateChatReplacementsHash(chatId: string | number, hash: string): Promise<void> {
    const id = String(chatId);
    await this.db.prepare(`
      UPDATE chats SET last_replacements_hash = ?, updated_at = ? WHERE chat_id = ?
    `).bind(hash, new Date().toISOString(), id).run();
  }

  async hasSeenNotification(chatId: string | number, notificationId: string): Promise<boolean> {
    const row = await this.db.prepare(`
      SELECT 1 FROM seen_notifications WHERE chat_id = ? AND notification_id = ?
    `).bind(String(chatId), notificationId).first();
    return !!row;
  }

  async markNotificationSeen(chatId: string | number, notificationId: string): Promise<void> {
    const id = `${chatId}_${notificationId}`;
    const now = new Date().toISOString();
    await this.db.prepare(`
      INSERT INTO seen_notifications (id, chat_id, notification_id, seen_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(id) DO NOTHING
    `).bind(id, String(chatId), notificationId, now).run();
  }

  // Survey session
  async getSurveySession(chatId: string, surveyId: string) {
    return await this.db.prepare(`
      SELECT * FROM survey_sessions WHERE chat_id = ? AND survey_id = ?
    `).bind(chatId, surveyId).first<any>();
  }

  async saveSurveySession(chatId: string, surveyId: string, questionIndex: number, answersJson: string, status: string = 'in_progress') {
    const id = `${chatId}_${surveyId}`;
    const now = new Date().toISOString();
    await this.db.prepare(`
      INSERT INTO survey_sessions (id, chat_id, survey_id, current_question_index, answers_json, status, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        current_question_index = excluded.current_question_index,
        answers_json = excluded.answers_json,
        status = excluded.status,
        updated_at = excluded.updated_at
    `).bind(id, chatId, surveyId, questionIndex, answersJson, status, now).run();
  }
}
