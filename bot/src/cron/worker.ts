import { TelegramApi, InlineKeyboardMarkup } from '../telegram/api';
import { DatabaseService } from '../db/d1';
import { ScheduleClient, formatRuDate, getDayOfWeekRuShort, escapeHtml } from '../schedule/client';
import { DeliveryBotClient } from '../delivery/client';
import { Env, ChatConfig, DayDto } from '../types';

interface ChangedLessonItem {
  date: string;
  number: number;
  startTime?: string;
  subject: string;
  state: string;
  room?: string;
  teacher?: string;
  group?: string;
}

export class CronWorker {
  constructor(
    private tg: TelegramApi,
    private db: DatabaseService,
    private schedule: ScheduleClient,
    private delivery: DeliveryBotClient,
    private env: Env
  ) {}

  async run(forceMode?: string): Promise<{ replacementsChecked: number; replacementsSent: number; dailySent: number; deliveryPushed: number }> {
    const todayInfo = getMoscowDate(0);
    const tomorrowInfo = getMoscowDate(1);
    const todayStr = todayInfo.dateStr;
    const tomorrowStr = tomorrowInfo.dateStr;

    let replacementsSent = 0;
    let dailySent = 0;

    // Cache schedules fetched during this run to avoid duplicate requests for common groups
    const scheduleCache = new Map<string, DayDto[] | null>();

    const getCachedOrFetchSchedule = async (institution: string, targetId: string, isTeacher: boolean): Promise<DayDto[] | null> => {
      const cacheKey = `${institution}:${isTeacher ? 't' : 'g'}:${targetId}`;
      if (scheduleCache.has(cacheKey)) {
        return scheduleCache.get(cacheKey) || null;
      }
      const days = isTeacher
        ? await this.schedule.fetchTeacherSchedule(institution, targetId)
        : await this.schedule.fetchGroupSchedule(institution, targetId);
      scheduleCache.set(cacheKey, days);
      return days;
    };

    const moscowHour = getMoscowHour();
    const currentHourStr = `${String(moscowHour).padStart(2, '0')}:00`;

    // Determine current delivery tag and target date
    const hourTag = forceMode ? `${todayStr}_${forceMode}` : `${todayStr}_${currentHourStr}`;
    const isMorning = forceMode === 'morning' || (!forceMode && moscowHour < 14);
    const targetDateStr = isMorning ? todayStr : tomorrowStr;
    const targetDow = isMorning ? todayInfo.dayOfWeek : tomorrowInfo.dayOfWeek;

    // 1. DAILY SCHEDULE DELIVERY
    const candidateChats = await this.db.getChatsForDailySchedule(hourTag);

    for (const chat of candidateChats) {
      const mode = chat.notify_tomorrow_time || 'off';
      if (mode === 'off' || !chat.group_name) continue;

      let wantsNow = false;
      if (forceMode === 'morning' || forceMode === 'evening') {
        wantsNow = true;
      } else if (mode === currentHourStr) {
        wantsNow = true;
      } else if (mode === 'morning' && moscowHour === 7) {
        wantsNow = true;
      } else if (mode === 'evening' && moscowHour === 19) {
        wantsNow = true;
      } else if (mode === 'both' && (moscowHour === 7 || moscowHour === 19)) {
        wantsNow = true;
      }

      if (!wantsNow) continue;

      const targetId = chat.group_id || chat.group_name;
      const isTeacher = chat.target_type === 'teacher';

      const days = await getCachedOrFetchSchedule(chat.institution, targetId, isTeacher);
      if (!days) continue;

      const targetDay = days.find(d => d.date === targetDateStr) || { date: targetDateStr, lessons: [] };

      // On Sunday, if there are no classes scheduled, skip sending empty "Пары отсутствуют" notifications
      if (targetDow === 0 && (!targetDay.lessons || targetDay.lessons.length === 0)) {
        await this.db.markDailySent(chat.chat_id, hourTag, chat.last_daily_sent_date);
        continue;
      }

      const res = await this.schedule.formatDaySchedule(
        targetDay,
        chat.group_name,
        isTeacher,
        'whatrushki_schedule_bot',
        chat.institution
      );

      const keyboard = buildDailyKeyboard(isMorning);

      try {
        await this.tg.sendMessage({
          chat_id: Number(chat.chat_id),
          text: res.text,
          parse_mode: 'HTML',
          reply_markup: keyboard
        });
        dailySent++;
        await this.db.markDailySent(chat.chat_id, hourTag, chat.last_daily_sent_date);

        // Synchronize replacement hash with full current schedule so daily notifications don't trigger redundant alerts
        const currentHash = computeReplacementsHash(days, todayStr, tomorrowStr, moscowHour);
        await this.db.updateChatReplacementsHash(chat.chat_id, currentHash);
        chat.last_replacements_hash = currentHash;
      } catch (e) {
        console.error('Failed to send daily digest to', chat.chat_id, e);
      }
    }

    // 2. INTRADAY REPLACEMENT NOTIFICATIONS
    // Only alerts when genuine NEW or UPDATED changes appear that were not already in the schedule
    const replacementChats = await this.db.getSubscribedChatsForReplacements();
    const replacementsChecked = replacementChats.length;

    for (const chat of replacementChats) {
      if (!chat.group_name) continue;
      const targetId = chat.group_id || chat.group_name;
      const isTeacher = chat.target_type === 'teacher';

      const days = await getCachedOrFetchSchedule(chat.institution, targetId, isTeacher);
      if (!days || days.length === 0) continue;

      const changedLessons = extractChangedLessons(days, todayStr, tomorrowStr, moscowHour);
      const currentHash = JSON.stringify(changedLessons);

      if (chat.last_replacements_hash === null || chat.last_replacements_hash === undefined) {
        // Initialize hash on first run without spamming
        await this.db.updateChatReplacementsHash(chat.chat_id, currentHash);
        continue;
      }

      if (chat.last_replacements_hash !== currentHash) {
        let prevChanged: ChangedLessonItem[] = [];
        try {
          prevChanged = JSON.parse(chat.last_replacements_hash || '[]');
        } catch {
          prevChanged = [];
        }

        // Find genuinely new or modified items (not in previous hash)
        const newlyChanged = changedLessons.filter(curr =>
          !prevChanged.some(prev =>
            prev.date === curr.date &&
            prev.number === curr.number &&
            prev.state === curr.state &&
            prev.subject === curr.subject &&
            prev.room === curr.room &&
            prev.teacher === curr.teacher &&
            prev.group === curr.group
          )
        );

        if (newlyChanged.length > 0) {
          const lines: string[] = [
            `⚡️ <b>Обнаружены новые изменения в расписании!</b>`,
            `👥 <b>${escapeHtml(chat.group_name)}</b>`,
            ''
          ];

          for (const item of newlyChanged) {
            const dayRu = formatRuDate(item.date);
            const dow = getDayOfWeekRuShort(item.date);
            const statusLabel = item.state === 'CANCELLED' ? '❌ [ОТМЕНЕНО]' : '⚠️ [ЗАМЕНА]';
            const isClassHour = item.number === 0 || item.subject.toLowerCase().includes('классный час');
            const pairLabel = isClassHour ? 'Классный час' : `${item.number} пара`;
            const timeLabel = item.startTime ? ` (${item.startTime})` : '';

            lines.push(`📌 <b>${dayRu} (${dow})</b> · <b>${pairLabel}</b>${timeLabel} ${statusLabel}`);
            if (!isClassHour || item.subject.toLowerCase() !== 'классный час') {
              lines.push(`<b>${escapeHtml(item.subject)}</b>`);
            }
            if (item.teacher) lines.push(`Преподаватель: ${escapeHtml(item.teacher)}`);
            if (item.room) lines.push(`Ауд. ${escapeHtml(item.room)}`);
            lines.push('');
          }

          const keyboard: InlineKeyboardMarkup = {
            inline_keyboard: [
              [
                { text: '📅 Сегодня', callback_data: 'cmd:today' },
                { text: '🗓 Завтра', callback_data: 'cmd:tomorrow' }
              ],
              [
                { text: '🗓 Вся неделя', callback_data: 'cmd:week' }
              ]
            ]
          };

          try {
            await this.tg.sendMessage({
              chat_id: Number(chat.chat_id),
              text: lines.join('\n').trim(),
              parse_mode: 'HTML',
              reply_markup: keyboard
            });
            replacementsSent++;
          } catch (e) {
            console.error('Failed to send replacement alert to', chat.chat_id, e);
          }
        }

        await this.db.updateChatReplacementsHash(chat.chat_id, currentHash);
      }
    }

        // 3. PUSH ACTIVE DELIVERY NOTIFICATIONS (Announcements & Surveys)
    let deliveryPushed = 0;
    try {
      const activeChats = await this.db.getSubscribedChatsForReplacements();
      const notifCache = new Map<string, any[]>();

      for (const chat of activeChats) {
        const inst = chat.institution || 'rksi';
        if (!notifCache.has(inst)) {
          const items = await this.delivery.fetchActiveNotifications(inst);
          notifCache.set(inst, items);
        }

        const items = notifCache.get(inst) || [];
        for (const item of items) {
          const alreadySeen = await this.db.hasSeenNotification(chat.chat_id, item.id);
          if (alreadySeen) continue;

          const lines = [
            `📢 <b>${escapeHtml(item.title)}</b>`,
            '',
            escapeHtml(item.message)
          ];

          const keyboard: InlineKeyboardMarkup = { inline_keyboard: [] };
          if (item.action_url && item.action_title) {
            keyboard.inline_keyboard.push([
              { text: item.action_title, url: item.action_url }
            ]);
          } else if (item.type === 'survey' && item.survey) {
            keyboard.inline_keyboard.push([
              { text: '📝 Пройти опрос', callback_data: `survey_start:${item.survey.survey_id}` }
            ]);
          }

          try {
            await this.tg.sendMessage({
              chat_id: Number(chat.chat_id),
              text: lines.join('\n'),
              parse_mode: 'HTML',
              reply_markup: keyboard.inline_keyboard.length > 0 ? keyboard : undefined
            });
            deliveryPushed++;
            await this.db.markNotificationSeen(chat.chat_id, item.id);
          } catch (e) {
            console.error('Failed to push delivery notif to', chat.chat_id, e);
          }
        }
      }
    } catch (e) {
      console.error('Error during delivery notifications push:', e);
    }

    return { replacementsChecked, replacementsSent, dailySent, deliveryPushed };
  }
}

function extractChangedLessons(days: DayDto[], todayStr: string, tomorrowStr: string, moscowHour: number): ChangedLessonItem[] {
  // If evening (>= 18 MSK), today's classes are already over, so only look at tomorrow
  const checkToday = moscowHour < 18;
  const activeDays = days.filter(d => (checkToday && d.date === todayStr) || d.date === tomorrowStr);
  const changedLessons: ChangedLessonItem[] = [];

  for (const d of activeDays) {
    for (const l of (d.lessons || [])) {
      if (l.state === 'CHANGED' || l.state === 'REPLACEMENT' || l.state === 'CANCELLED') {
        const u = l.otUnits?.[0];
        changedLessons.push({
          date: d.date,
          number: l.number,
          startTime: l.startTime || '',
          subject: l.subject || '',
          state: l.state,
          room: u?.room || '',
          teacher: u?.teacher || '',
          group: u?.group || ''
        });
      }
    }
  }

  changedLessons.sort((a, b) => {
    const dCmp = a.date.localeCompare(b.date);
    if (dCmp !== 0) return dCmp;
    if (a.startTime && b.startTime) {
      const tCmp = a.startTime.localeCompare(b.startTime);
      if (tCmp !== 0) return tCmp;
    }
    return a.number - b.number;
  });
  return changedLessons;
}

function computeReplacementsHash(days: DayDto[], todayStr: string, tomorrowStr: string, moscowHour: number): string {
  return JSON.stringify(extractChangedLessons(days, todayStr, tomorrowStr, moscowHour));
}

function getMoscowHour(): number {
  const d = new Date(Date.now() + 3 * 3600 * 1000);
  return d.getUTCHours();
}

function getMoscowDate(offsetDays = 0): { dateStr: string; dayOfWeek: number } {
  const d = new Date(Date.now() + 3 * 3600 * 1000 + offsetDays * 24 * 3600 * 1000);
  const dateStr = d.toISOString().split('T')[0];
  const dayOfWeek = d.getUTCDay(); // 0 = Sunday
  return { dateStr, dayOfWeek };
}

function buildDailyKeyboard(isMorning: boolean): InlineKeyboardMarkup {
  const rows: any[][] = [];

  if (isMorning) {
    rows.push([
      { text: '🗓 На завтра', callback_data: 'cmd:tomorrow' },
      { text: '🗓 Вся неделя', callback_data: 'cmd:week' }
    ]);
  } else {
    rows.push([
      { text: '📅 На сегодня', callback_data: 'cmd:today' },
      { text: '🗓 Вся неделя', callback_data: 'cmd:week' }
    ]);
  }

  rows.push([{ text: '« В главное меню', callback_data: 'cmd:menu' }]);
  return { inline_keyboard: rows };
}
