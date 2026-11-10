import { TelegramApi, InlineKeyboardMarkup } from './api';
import { DatabaseService } from '../db/d1';
import { ScheduleClient, INSTITUTIONS, escapeHtml, formatRuDate, getDayOfWeekRuShort, fromHex, toHex } from '../schedule/client';
import { DeliveryBotClient } from '../delivery/client';
import { Env, ChatConfig, GroupMetaDto, TeacherMetaDto } from '../types';

export class BotHandlers {
  private botUsername = 'whatrushki_schedule_bot';

  constructor(
    private tg: TelegramApi,
    private db: DatabaseService,
    private schedule: ScheduleClient,
    private delivery: DeliveryBotClient,
    private env: Env
  ) {}

  async handleStart(
    chatId: number,
    from: any,
    chatType: string,
    title?: string,
    messageId?: number,
    args?: string[]
  ): Promise<void> {
    await this.db.upsertChat({ chat_id: String(chatId), chat_type: chatType, title });

    // Handle deep-links (e.g. /start t_rksi_<hex> or /start g_rksi_<hex>)
    if (args && args.length > 0) {
      const param = args[0];
      if (param.startsWith('t_')) {
        const parts = param.split('_');
        const inst = parts[1] || 'rksi';
        const raw = parts.slice(2).join('_');
        const teacherIdOrName = fromHex(raw);
        await this.showTeacherSchedule(chatId, inst, teacherIdOrName, 0, messageId);
        return;
      }
      if (param.startsWith('g_')) {
        const parts = param.split('_');
        const inst = parts[1] || 'rksi';
        const raw = parts.slice(2).join('_');
        const groupNameOrId = fromHex(raw);
        await this.showGroupSchedule(chatId, inst, groupNameOrId, 0, messageId);
        return;
      }
    }

    const chat = await this.db.getChat(chatId);

    // If group/teacher is not selected yet, show institution picker
    if (!chat || !chat.group_name) {
      await this.showInstitutionPicker(chatId, messageId);
      return;
    }

    // Main Screen
    await this.showMainScheduleMenu(chatId, chat, messageId);
  }

  async showInstitutionPicker(chatId: number, messageId?: number): Promise<void> {
    await this.db.setPendingAction(chatId, null);

    const text = [
      `🏛 <b>W.H.A.T. Расписание</b>`,
      '',
      `Выберите учебное заведение:`
    ].join('\n');

    const keyboard: InlineKeyboardMarkup = {
      inline_keyboard: [
        [
          { text: '🏛 РКСИ', callback_data: 'inst_select:rksi' },
          { text: '🎓 ДГТУ', callback_data: 'inst_select:dgtu' }
        ],
        [
          { text: '📚 ЮФУ', callback_data: 'inst_select:sfedu' },
          { text: '🏢 РИНХ', callback_data: 'inst_select:rinh' }
        ],
        [
          { text: '🏫 ИУБиП', callback_data: 'inst_select:iubip' },
          { text: '🎓 ТвГУ', callback_data: 'inst_select:tvgu' }
        ]
      ]
    };

    if (messageId) {
      await this.tg.editMessageText({
        chat_id: chatId,
        message_id: messageId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    } else {
      await this.tg.sendMessage({
        chat_id: chatId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    }
  }

  // --- INSTITUTION HUB ---
  async handleInstitutionSelect(chatId: number, instId: string, messageId?: number): Promise<void> {
    const inst = INSTITUTIONS.find(i => i.id === instId) || { emoji: '🏛', shortName: instId.toUpperCase(), name: '' };

    const text = [
      `🏛 <b>${inst.shortName}</b> (${inst.name})`,
      '',
      `Выберите, чье расписание вам нужно:`
    ].join('\n');

    const keyboard: InlineKeyboardMarkup = {
      inline_keyboard: [
        [
          { text: '👥 Выбрать группу', callback_data: `inst_mode:group:${instId}` },
          { text: '🎓 Выбрать преподавателя', callback_data: `inst_mode:teacher:${instId}` }
        ],
        [
          { text: '📚 Выбрать по курсам', callback_data: `inst_courses:${instId}` }
        ],
        [
          { text: '« Выбрать другой вуз', callback_data: 'menu:change_inst' }
        ]
      ]
    };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async promptGroupSearch(chatId: number, instId: string, messageId?: number): Promise<void> {
    await this.db.setPendingAction(chatId, `search_group:${instId}`);
    const inst = INSTITUTIONS.find(i => i.id === instId) || { emoji: '🎓', shortName: instId.toUpperCase(), name: '' };

    const text = [
      `👥 <b>${inst.shortName} · Поиск группы</b>`,
      '',
      `Напишите в чат номер или часть названия группы.`,
      `<i>Примеры: <code>фиит 1</code>, <code>информатика 2</code>, <code>ИС-21</code>, <code>AMCE11</code></i>`
    ].join('\n');

    const keyboard: InlineKeyboardMarkup = {
      inline_keyboard: [
        [
          { text: '📚 Выбрать по курсу', callback_data: `inst_courses:${instId}` }
        ],
        [
          { text: '« Назад', callback_data: `inst_select:${instId}` }
        ]
      ]
    };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async promptTeacherSearch(chatId: number, instId: string, messageId?: number): Promise<void> {
    await this.db.setPendingAction(chatId, `search_teacher:${instId}`);
    const inst = INSTITUTIONS.find(i => i.id === instId) || { emoji: '🎓', shortName: instId.toUpperCase(), name: '' };

    const text = [
      `🎓 <b>${inst.shortName} · Поиск преподавателя</b>`,
      '',
      `Напишите в чат фамилию преподавателя:`,
      `<i>Примеры: <code>Ужегова</code>, <code>Акопян</code>, <code>Каламбет</code></i>`,
      '',
      `Или выберите первую букву фамилии:`
    ].join('\n');

    const letters = ['А', 'Б', 'В', 'Г', 'Д', 'И', 'К', 'Л', 'М', 'П', 'С', 'У', 'Ч', 'Ш'];
    const letterRows: any[][] = [];
    const rowSize = 5;

    for (let i = 0; i < letters.length; i += rowSize) {
      letterRows.push(
        letters.slice(i, i + rowSize).map(letter => ({
          text: letter,
          callback_data: `t_letter:${instId}:${letter}`
        }))
      );
    }

    letterRows.push([{ text: '« Назад', callback_data: `inst_select:${instId}` }]);

    const keyboard: InlineKeyboardMarkup = { inline_keyboard: letterRows };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async showCoursesMenu(chatId: number, instId: string, messageId?: number): Promise<void> {
    const inst = INSTITUTIONS.find(i => i.id === instId) || { emoji: '🏛', shortName: instId.toUpperCase(), name: '' };
    const allGroups = await this.schedule.fetchGroups(instId);

    const coursesSet = new Set<number>();
    for (const g of allGroups) {
      if (typeof g.course === 'number' && g.course > 0 && g.course <= 6) {
        coursesSet.add(g.course);
      } else {
        const m = g.name.match(/-(\d)/);
        if (m) coursesSet.add(parseInt(m[1], 10));
      }
    }

    const courses = Array.from(coursesSet).sort((a, b) => a - b);
    if (courses.length === 0) courses.push(1, 2, 3, 4);

    const rows: any[][] = [];
    const numLabels = ['1️⃣ 1 курс', '2️⃣ 2 курс', '3️⃣ 3 курс', '4️⃣ 4 курс', '5️⃣ 5 курс', '6️⃣ 6 курс'];

    for (let i = 0; i < courses.length; i += 2) {
      const row = courses.slice(i, i + 2).map(c => ({
        text: numLabels[c - 1] || `${c} курс`,
        callback_data: `course_sel:${instId}:${c}`
      }));
      rows.push(row);
    }

    rows.push([{ text: '« Назад', callback_data: `inst_select:${instId}` }]);

    const text = `📚 <b>${inst.shortName} · Выбор курса</b>\n\nНа каком курсе вы учитесь?`;
    const keyboard: InlineKeyboardMarkup = { inline_keyboard: rows };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async handleCourseSelect(chatId: number, instId: string, courseNum: number, messageId?: number): Promise<void> {
    const allGroups = await this.schedule.fetchGroups(instId);
    const filtered = allGroups.filter(g => {
      if (g.course && g.course === courseNum) return true;
      const numMatch = g.name.match(/-(\d)/);
      if (numMatch && parseInt(numMatch[1], 10) === courseNum) return true;
      return false;
    });

    const inst = INSTITUTIONS.find(i => i.id === instId) || { shortName: instId.toUpperCase() };

    // If <= 30 groups (like in SFEDU, RKSI, IUBIP), show group buttons directly!
    if (filtered.length <= 30 && filtered.length > 0) {
      const text = `🏛 <b>${inst.shortName} · ${courseNum} курс</b> (${filtered.length} групп)\nВыберите группу:`;
      const keyboard = this.buildGroupButtons(filtered, instId, `inst_courses:${instId}`);

      if (messageId) {
        await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
      } else {
        await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
      }
      return;
    }

    // Large course (> 30 groups, like DGTU or RINH) -> offer first letter buttons!
    const lettersSet = new Set<string>();
    for (const g of filtered) {
      if (g.name && g.name.length > 0) {
        lettersSet.add(g.name[0].toUpperCase());
      }
    }
    const letters = Array.from(lettersSet).sort();

    const letterRows: any[][] = [];
    const rowSize = 4;
    for (let i = 0; i < letters.length; i += rowSize) {
      letterRows.push(
        letters.slice(i, i + rowSize).map(letter => ({
          text: letter,
          callback_data: `g_clet:${instId}:${courseNum}:${encodeURIComponent(letter)}`
        }))
      );
    }

    letterRows.push([
      { text: '🔍 Найти группу текстом', callback_data: `group_search_prompt:${instId}` }
    ]);
    letterRows.push([
      { text: '« Назад к курсам', callback_data: `inst_courses:${instId}` }
    ]);

    const text = [
      `📚 <b>${inst.shortName} · ${courseNum} курс</b> (${filtered.length} групп)`,
      '',
      `Выберите первую букву названия группы:`
    ].join('\n');

    const keyboard: InlineKeyboardMarkup = { inline_keyboard: letterRows };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async handleCourseLetterGroups(chatId: number, instId: string, courseNum: number, letter: string, messageId?: number): Promise<void> {
    const allGroups = await this.schedule.fetchGroups(instId);
    const filtered = allGroups.filter(g => {
      const matchCourse = (g.course && g.course === courseNum) || (g.name.match(/-(\d)/) && parseInt(g.name.match(/-(\d)/)![1], 10) === courseNum);
      const matchLetter = g.name.toUpperCase().startsWith(letter.toUpperCase());
      return matchCourse && matchLetter;
    });

    const inst = INSTITUTIONS.find(i => i.id === instId) || { shortName: instId.toUpperCase() };
    const text = `🏛 <b>${inst.shortName} · ${courseNum} курс (${letter})</b>\nВыберите группу:`;
    const keyboard = this.buildGroupButtons(filtered.slice(0, 30), instId, `course_sel:${instId}:${courseNum}`);

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async handleTeacherLetter(chatId: number, instId: string, letter: string, messageId?: number): Promise<void> {
    const allTeachers = await this.schedule.fetchTeachers(instId);
    const filtered = allTeachers.filter(t => t.name.toUpperCase().startsWith(letter.toUpperCase()));

    const inst = INSTITUTIONS.find(i => i.id === instId) || { shortName: instId.toUpperCase() };
    const text = `🎓 <b>${inst.shortName} · Преподаватели на букву «${letter}»</b>:\nВыберите преподавателя:`;

    const rows: any[][] = [];
    for (let i = 0; i < Math.min(filtered.length, 24); i += 2) {
      rows.push(
        filtered.slice(i, i + 2).map(t => ({
          text: t.name,
          callback_data: `t_view:${instId}:${t.id}`
        }))
      );
    }

    rows.push([{ text: '« Назад к поиску', callback_data: `inst_mode:teacher:${instId}` }]);
    const keyboard: InlineKeyboardMarkup = { inline_keyboard: rows };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  // --- UNIFIED SMART SEARCH ---
  async handleUserTextInput(chatId: number, text: string): Promise<void> {
    const chat = await this.db.getChat(chatId);
    let inst = chat?.institution || 'rksi';

    if (chat?.pending_action?.startsWith('search_group:') || chat?.pending_action?.startsWith('search_teacher:')) {
      inst = chat.pending_action.split(':')[1] || inst;
    }

    const [groupMatches, teacherMatches] = await Promise.all([
      this.schedule.searchGroups(inst, text),
      this.schedule.searchTeachers(inst, text)
    ]);

    const totalMatches = groupMatches.length + teacherMatches.length;

    if (totalMatches === 0) {
      // Smart Fallback across other institutions
      const altInst = inst === 'rksi' ? 'dgtu' : 'rksi';
      const [altGroups, altTeachers] = await Promise.all([
        this.schedule.searchGroups(altInst, text),
        this.schedule.searchTeachers(altInst, text)
      ]);

      if (altGroups.length > 0 || altTeachers.length > 0) {
        const altInfo = INSTITUTIONS.find(i => i.id === altInst)?.shortName || altInst.toUpperCase();
        const msg = `⚠️ В <b>${inst.toUpperCase()}</b> ничего не найдено, но найдено в <b>${altInfo}</b>!`;
        const keyboard = this.buildSearchResultsButtons(altGroups.slice(0, 10), altTeachers.slice(0, 8), altInst);
        await this.tg.sendMessage({ chat_id: chatId, text: msg, parse_mode: 'HTML', reply_markup: keyboard });
        return;
      }

      await this.tg.sendMessage({
        chat_id: chatId,
        text: `⚠️ По запросу <code>${escapeHtml(text)}</code> ничего не найдено.\nПопробуйте ввести часть названия группы (например: <code>фиит 1</code>, <code>ИС</code>, <code>21</code>) или фамилию преподавателя.`,
        parse_mode: 'HTML'
      });
      return;
    }

    // Exact single match
    if (groupMatches.length === 1 && teacherMatches.length === 0) {
      const g = groupMatches[0];
      await this.db.setChatTarget(chatId, inst, 'group', g.name, g.id);
      const updatedChat = await this.db.getChat(chatId);
      if (updatedChat) await this.showMainScheduleMenu(chatId, updatedChat);
      return;
    }

    if (teacherMatches.length === 1 && groupMatches.length === 0) {
      const t = teacherMatches[0];
      await this.showTeacherSchedule(chatId, inst, t.id, 0);
      return;
    }

    // Multiple matches
    const msg = `🔍 Результаты поиска по запросу «<code>${escapeHtml(text)}</code>»:`;
    const keyboard = this.buildSearchResultsButtons(groupMatches.slice(0, 12), teacherMatches.slice(0, 8), inst);

    await this.tg.sendMessage({
      chat_id: chatId,
      text: msg,
      parse_mode: 'HTML',
      reply_markup: keyboard
    });
  }

  buildSearchResultsButtons(groups: GroupMetaDto[], teachers: TeacherMetaDto[], inst: string): InlineKeyboardMarkup {
    const inline_keyboard: any[][] = [];

    // Group buttons with adaptive columns:
    const maxGroupLen = Math.max(...groups.map(g => g.name.length), 0);
    const groupRowSize = maxGroupLen > 18 ? 1 : (maxGroupLen > 10 ? 2 : 3);

    for (let i = 0; i < groups.length; i += groupRowSize) {
      const row = groups.slice(i, i + groupRowSize).map(g => ({
        text: `👥 ${g.name}`,
        callback_data: `pick_grp:${inst}:${g.id}`
      }));
      inline_keyboard.push(row);
    }

    // Teacher buttons (2 per row)
    const teacherRowSize = 2;
    for (let i = 0; i < teachers.length; i += teacherRowSize) {
      const row = teachers.slice(i, i + teacherRowSize).map(t => ({
        text: `🎓 ${t.name}`,
        callback_data: `t_view:${inst}:${t.id}`
      }));
      inline_keyboard.push(row);
    }

    inline_keyboard.push([
      { text: '« В главное меню', callback_data: 'cmd:menu' }
    ]);

    return { inline_keyboard };
  }

  buildGroupButtons(groups: GroupMetaDto[], inst: string, backAction: string): InlineKeyboardMarkup {
    const inline_keyboard: any[][] = [];

    // Adaptive row size depending on group name length:
    const maxLen = Math.max(...groups.map(g => g.name.length), 0);
    const rowSize = maxLen > 18 ? 1 : (maxLen > 9 ? 2 : 3);

    for (let i = 0; i < groups.length; i += rowSize) {
      const row = groups.slice(i, i + rowSize).map(g => ({
        text: g.name,
        callback_data: `pick_grp:${inst}:${g.id}`
      }));
      inline_keyboard.push(row);
    }

    inline_keyboard.push([
      { text: '« Назад', callback_data: backAction }
    ]);

    return { inline_keyboard };
  }

  // --- MAIN MENU ---
  async showMainScheduleMenu(chatId: number, chat: ChatConfig, messageId?: number): Promise<void> {
    const inst = INSTITUTIONS.find(i => i.id === chat.institution) || { emoji: '🏛', shortName: chat.institution.toUpperCase() };
    const replBadge = chat.notify_replacements === 1 ? '🔔 Замены: Вкл' : '🔕 Замены: Выкл';
    let scheduleBadge = '⏰ Рассылка: Выкл';
    const notifyMode = chat.notify_tomorrow_time || 'off';
    if (notifyMode === 'morning' || notifyMode === '07:00') {
      scheduleBadge = '⏰ Утром (07:00)';
    } else if (notifyMode === 'evening' || notifyMode === '19:00') {
      scheduleBadge = '⏰ Вечером (19:00)';
    } else if (notifyMode === 'both') {
      scheduleBadge = '⏰ Утро и вечер';
    } else if (notifyMode !== 'off') {
      scheduleBadge = `⏰ В ${notifyMode} МСК`;
    }

    const isTeacher = chat.target_type === 'teacher';
    const targetLabel = isTeacher
      ? `Преподаватель ${escapeHtml(chat.group_name || '')}`
      : `Группа ${escapeHtml(chat.group_name || '').toUpperCase()}`;

    const text = [
      `🏛 <b>${inst.shortName} · ${targetLabel}</b>`,
      '',
      `Выберите действие:`
    ].join('\n');

    const inline_keyboard: any[][] = [
      [
        { text: '📅 Сегодня', callback_data: 'cmd:today' },
        { text: '🗓 Завтра', callback_data: 'cmd:tomorrow' }
      ],
      [
        { text: '🗓 Вся неделя', callback_data: 'cmd:week' }
      ],
      [
        { text: replBadge, callback_data: 'toggle:replacements' },
        { text: scheduleBadge, callback_data: 'menu:notify_time' }
      ],
      [
        { text: '⚙️ Сменить группу / преподавателя', callback_data: 'menu:change_hub' }
      ]
    ];

    // Check for active Delivery survey
    try {
      const activeSurveys = await this.delivery.fetchActiveNotifications(chat.institution);
      const survey = activeSurveys.find(s => s.type === 'survey' && s.survey);
      if (survey && survey.survey) {
        const session = await this.db.getSurveySession(String(chatId), survey.survey.survey_id);
        if (!session || session.status !== 'completed') {
          inline_keyboard.push([
            { text: `📝 Опрос: ${survey.title}`, callback_data: `survey_start:${survey.survey.survey_id}` }
          ]);
        }
      }
    } catch (_) {}

    const keyboard: InlineKeyboardMarkup = { inline_keyboard };

    if (messageId) {
      await this.tg.editMessageText({
        chat_id: chatId,
        message_id: messageId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    } else {
      await this.tg.sendMessage({
        chat_id: chatId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    }
  }

  async showNotifyTimePicker(chatId: number, chat: ChatConfig, messageId?: number): Promise<void> {
    const currentMode = chat.notify_tomorrow_time || 'off';
    const isMorning = currentMode === 'morning' || currentMode === '07:00';
    const isEvening = currentMode === 'evening' || currentMode === '19:00';
    const isBoth = currentMode === 'both';
    const isOff = currentMode === 'off';
    const isCustom = !isMorning && !isEvening && !isBoth && !isOff;

    const currentDisplay = isOff
      ? 'Отключено'
      : (isCustom ? `В ${currentMode} МСК` : (isBoth ? 'Утро (07:00) и вечер (19:00)' : (isMorning ? 'Утром (07:00)' : 'Вечером (19:00)')));

    const text = [
      `⏰ <b>Автоматическая рассылка расписания</b>`,
      '',
      `Текущий режим: <b>${currentDisplay}</b>`,
      '',
      `Выберите удобное время отправки расписания в чат:`,
      `• <b>Утром (07:00 МСК)</b> — расписание на сегодня`,
      `• <b>Вечером (19:00 МСК)</b> — расписание на завтра`,
      `• <b>Утром и вечером</b> — накануне вечером и напоминание утром`,
      `• <b>Выбрать точный час (06:00 – 22:00)</b> — любое время каждый час`,
      '',
      `<i>💡 В выходные дни бот не беспокоит пустыми сообщениями.</i>`
    ].join('\n');

    const keyboard: InlineKeyboardMarkup = {
      inline_keyboard: [
        [
          { text: `${isMorning ? '✅ ' : ''}🌅 Утром (07:00 на сегодня)`, callback_data: 'set_notify:07:00' }
        ],
        [
          { text: `${isEvening ? '✅ ' : ''}🌇 Вечером (19:00 на завтра)`, callback_data: 'set_notify:19:00' }
        ],
        [
          { text: `${isBoth ? '✅ ' : ''}🔄 Утром (07:00) и вечером (19:00)`, callback_data: 'set_notify:both' }
        ],
        [
          { text: `${isCustom ? `✅ 🕒 Выбрать час (сейчас: ${currentMode})` : '🕒 Выбрать любой час (06:00–22:00)'}`, callback_data: 'menu:notify_hours' }
        ],
        [
          { text: `${isOff ? '✅ ' : ''}❌ Отключить рассылку`, callback_data: 'set_notify:off' }
        ],
        [
          { text: '« В главное меню', callback_data: 'cmd:menu' }
        ]
      ]
    };

    if (messageId) {
      await this.tg.editMessageText({
        chat_id: chatId,
        message_id: messageId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    } else {
      await this.tg.sendMessage({
        chat_id: chatId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    }
  }

  async showHourlyPicker(chatId: number, chat: ChatConfig, messageId?: number): Promise<void> {
    const currentMode = chat.notify_tomorrow_time || 'off';

    const text = [
      `🕒 <b>Выбор часа автоматической рассылки (МСК)</b>`,
      '',
      `Текущее время: <b>${currentMode === 'off' ? 'Отключено' : currentMode}</b>`,
      '',
      `• <b>06:00 – 13:00</b>: рассылка на <b>сегодня</b>`,
      `• <b>14:00 – 22:00</b>: рассылка на <b>завтра</b>`,
      '',
      `Выберите час отправки расписания:`
    ].join('\n');

    const hours = [
      '06:00', '07:00', '08:00', '09:00',
      '10:00', '11:00', '12:00', '13:00',
      '14:00', '15:00', '16:00', '17:00',
      '18:00', '19:00', '20:00', '21:00',
      '22:00'
    ];

    const rows: any[][] = [];
    let currentRow: any[] = [];

    for (const h of hours) {
      const isSel = currentMode === h;
      currentRow.push({
        text: `${isSel ? '✅ ' : ''}${h}`,
        callback_data: `set_notify:${h}`
      });
      if (currentRow.length === 4) {
        rows.push(currentRow);
        currentRow = [];
      }
    }
    if (currentRow.length > 0) {
      rows.push(currentRow);
    }

    rows.push([{ text: '« Назад в настройки рассылки', callback_data: 'menu:notify_time' }]);

    const keyboard: InlineKeyboardMarkup = { inline_keyboard: rows };

    if (messageId) {
      await this.tg.editMessageText({
        chat_id: chatId,
        message_id: messageId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    } else {
      await this.tg.sendMessage({
        chat_id: chatId,
        text,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    }
  }

  // --- TODAY / TOMORROW / WEEK ---
  async handleToday(chatId: number, messageId?: number): Promise<void> {
    const chat = await this.db.getChat(chatId);
    if (!chat || !chat.group_name) {
      await this.showInstitutionPicker(chatId, messageId);
      return;
    }

    const isTeacher = chat.target_type === 'teacher';
    const scheduleDays = isTeacher
      ? await this.schedule.fetchTeacherSchedule(chat.institution, chat.group_id || chat.group_name)
      : await this.schedule.fetchGroupSchedule(chat.institution, chat.group_id || chat.group_name);

    if (!scheduleDays || scheduleDays.length === 0) {
      const text = `⚠️ Расписание для <b>${escapeHtml(chat.group_name)}</b> временно недоступно в кэше.`;
      if (messageId) {
        await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML' });
      } else {
        await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML' });
      }
      return;
    }

    const todayDateStr = getMoscowDateString(0);
    const day = scheduleDays.find(d => d.date === todayDateStr) || { date: todayDateStr, lessons: [] };

    const res = await this.schedule.formatDaySchedule(
      day,
      chat.group_name,
      isTeacher,
      this.botUsername,
      chat.institution
    );

    const keyboard = this.buildScheduleCardKeyboard('today');
    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async handleTomorrow(chatId: number, messageId?: number): Promise<void> {
    const chat = await this.db.getChat(chatId);
    if (!chat || !chat.group_name) {
      await this.showInstitutionPicker(chatId, messageId);
      return;
    }

    const isTeacher = chat.target_type === 'teacher';
    const scheduleDays = isTeacher
      ? await this.schedule.fetchTeacherSchedule(chat.institution, chat.group_id || chat.group_name)
      : await this.schedule.fetchGroupSchedule(chat.institution, chat.group_id || chat.group_name);

    if (!scheduleDays || scheduleDays.length === 0) {
      const text = `⚠️ Расписание для <b>${escapeHtml(chat.group_name)}</b> не найдено.`;
      if (messageId) {
        await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML' });
      } else {
        await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML' });
      }
      return;
    }

    const tomorrowDateStr = getMoscowDateString(1);
    const day = scheduleDays.find(d => d.date === tomorrowDateStr) || { date: tomorrowDateStr, lessons: [] };

    const res = await this.schedule.formatDaySchedule(
      day,
      chat.group_name,
      isTeacher,
      this.botUsername,
      chat.institution
    );

    const keyboard = this.buildScheduleCardKeyboard('tomorrow');
    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async handleWeek(chatId: number, messageId?: number): Promise<void> {
    const chat = await this.db.getChat(chatId);
    if (!chat || !chat.group_name) {
      await this.showInstitutionPicker(chatId, messageId);
      return;
    }

    const isTeacher = chat.target_type === 'teacher';
    const scheduleDays = isTeacher
      ? await this.schedule.fetchTeacherSchedule(chat.institution, chat.group_id || chat.group_name)
      : await this.schedule.fetchGroupSchedule(chat.institution, chat.group_id || chat.group_name);

    if (!scheduleDays || scheduleDays.length === 0) {
      const text = '⚠️ Расписание на неделю не найдено.';
      if (messageId) {
        await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML' });
      } else {
        await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML' });
      }
      return;
    }

    const text = await this.schedule.formatWeekSchedule(
      scheduleDays.slice(0, 6),
      chat.group_name,
      isTeacher,
      this.botUsername,
      chat.institution
    );

    const keyboard: InlineKeyboardMarkup = {
      inline_keyboard: [
        [
          { text: '📅 Сегодня', callback_data: 'cmd:today' },
          { text: '🗓 Завтра', callback_data: 'cmd:tomorrow' }
        ],
        [
          { text: '« В главное меню', callback_data: 'cmd:menu' }
        ]
      ]
    };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  // --- TEACHER SCHEDULE VIEW ---
  async showTeacherSchedule(
    chatId: number,
    inst: string,
    teacherIdOrName: string,
    dayOffset = 0,
    messageId?: number
  ): Promise<void> {
    const [scheduleDays, teachers] = await Promise.all([
      this.schedule.fetchTeacherSchedule(inst, teacherIdOrName),
      this.schedule.fetchTeachers(inst)
    ]);

    const teacher = teachers.find(t => t.id === teacherIdOrName || t.name === teacherIdOrName);
    const teacherId = teacher?.id || teacherIdOrName;
    const teacherName = teacher?.name || teacherIdOrName;

    if (!scheduleDays || scheduleDays.length === 0) {
      const msg = `⚠️ Расписание преподавателя <b>${escapeHtml(teacherName)}</b> временно отсутствует.`;
      if (messageId) {
        await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text: msg, parse_mode: 'HTML' });
      } else {
        await this.tg.sendMessage({ chat_id: chatId, text: msg, parse_mode: 'HTML' });
      }
      return;
    }

    const targetDateStr = getMoscowDateString(dayOffset);
    const day = scheduleDays.find(d => d.date === targetDateStr) || { date: targetDateStr, lessons: [] };

    const dateFormatted = formatRuDate(day.date);
    const dayOfWeekShort = getDayOfWeekRuShort(day.date);
    const customHeader = `<blockquote>${escapeHtml(teacherName)} · ${dateFormatted} (${dayOfWeekShort})</blockquote>`;

    const res = await this.schedule.formatDaySchedule(
      day,
      teacherName,
      true,
      this.botUsername,
      inst,
      customHeader
    );

    const navButtons: any[][] = [
      [
        { text: '📅 Сегодня', callback_data: `t_today:${inst}:${teacherId}` },
        { text: '🗓 Завтра', callback_data: `t_tomorrow:${inst}:${teacherId}` }
      ],
      [
        { text: '🗓 Вся неделя', callback_data: `t_week:${inst}:${teacherId}` }
      ],
      [
        { text: '📌 Выбрать моим расписанием', callback_data: `pick_tchr:${inst}:${teacherId}` }
      ],
      [
        { text: '« В главное меню', callback_data: 'cmd:menu' }
      ]
    ];

    const keyboard: InlineKeyboardMarkup = { inline_keyboard: navButtons };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async showTeacherWeekSchedule(chatId: number, inst: string, teacherId: string, messageId?: number): Promise<void> {
    const [scheduleDays, teacherName] = await Promise.all([
      this.schedule.fetchTeacherSchedule(inst, teacherId),
      this.schedule.getTeacherName(inst, teacherId)
    ]);

    if (!scheduleDays || scheduleDays.length === 0) {
      await this.tg.sendMessage({ chat_id: chatId, text: `⚠️ Расписание на неделю не найдено.` });
      return;
    }

    const text = await this.schedule.formatWeekSchedule(
      scheduleDays.slice(0, 6),
      teacherName,
      true,
      this.botUsername,
      inst
    );

    const keyboard: InlineKeyboardMarkup = {
      inline_keyboard: [
        [
          { text: '📅 Сегодня', callback_data: `t_today:${inst}:${teacherId}` },
          { text: '🗓 Завтра', callback_data: `t_tomorrow:${inst}:${teacherId}` }
        ],
        [
          { text: '« В главное меню', callback_data: 'cmd:menu' }
        ]
      ]
    };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  // --- GROUP SCHEDULE VIEW ---
  async showGroupSchedule(
    chatId: number,
    inst: string,
    groupNameOrId: string,
    dayOffset = 0,
    messageId?: number
  ): Promise<void> {
    const [scheduleDays, resolvedName, resolvedId] = await Promise.all([
      this.schedule.fetchGroupSchedule(inst, groupNameOrId),
      this.schedule.resolveGroupName(inst, groupNameOrId),
      this.schedule.resolveGroupId(inst, groupNameOrId)
    ]);

    if (!scheduleDays || scheduleDays.length === 0) {
      const msg = `⚠️ Расписание группы <b>${escapeHtml(resolvedName)}</b> временно отсутствует.`;
      if (messageId) {
        await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text: msg, parse_mode: 'HTML' });
      } else {
        await this.tg.sendMessage({ chat_id: chatId, text: msg, parse_mode: 'HTML' });
      }
      return;
    }

    const targetDateStr = getMoscowDateString(dayOffset);
    const day = scheduleDays.find(d => d.date === targetDateStr) || { date: targetDateStr, lessons: [] };

    const dateFormatted = formatRuDate(day.date);
    const dayOfWeekShort = getDayOfWeekRuShort(day.date);
    const customHeader = `<blockquote>${escapeHtml(resolvedName)} · ${dateFormatted} (${dayOfWeekShort})</blockquote>`;

    const res = await this.schedule.formatDaySchedule(
      day,
      resolvedName,
      false,
      this.botUsername,
      inst,
      customHeader
    );

    const navButtons: any[][] = [
      [
        { text: '📅 Сегодня', callback_data: `g_today:${inst}:${resolvedId}` },
        { text: '🗓 Завтра', callback_data: `g_tomorrow:${inst}:${resolvedId}` }
      ],
      [
        { text: '🗓 Вся неделя', callback_data: `g_week:${inst}:${resolvedId}` }
      ],
      [
        { text: '📌 Выбрать моим расписанием', callback_data: `pick_grp:${inst}:${resolvedId}` }
      ],
      [
        { text: '« В главное меню', callback_data: 'cmd:menu' }
      ]
    ];

    const keyboard: InlineKeyboardMarkup = { inline_keyboard: navButtons };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text: res.text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  async showGroupWeekSchedule(chatId: number, inst: string, groupNameOrId: string, messageId?: number): Promise<void> {
    const [scheduleDays, resolvedName, resolvedId] = await Promise.all([
      this.schedule.fetchGroupSchedule(inst, groupNameOrId),
      this.schedule.resolveGroupName(inst, groupNameOrId),
      this.schedule.resolveGroupId(inst, groupNameOrId)
    ]);

    if (!scheduleDays || scheduleDays.length === 0) {
      await this.tg.sendMessage({ chat_id: chatId, text: `⚠️ Расписание на неделю не найдено.` });
      return;
    }

    const text = await this.schedule.formatWeekSchedule(
      scheduleDays.slice(0, 6),
      resolvedName,
      false,
      this.botUsername,
      inst
    );

    const keyboard: InlineKeyboardMarkup = {
      inline_keyboard: [
        [
          { text: '📅 Сегодня', callback_data: `g_today:${inst}:${resolvedId}` },
          { text: '🗓 Завтра', callback_data: `g_tomorrow:${inst}:${resolvedId}` }
        ],
        [
          { text: '« В главное меню', callback_data: 'cmd:menu' }
        ]
      ]
    };

    if (messageId) {
      await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', reply_markup: keyboard });
    } else {
      await this.tg.sendMessage({ chat_id: chatId, text, parse_mode: 'HTML', reply_markup: keyboard });
    }
  }

  private buildScheduleCardKeyboard(
    currentDay: 'today' | 'tomorrow'
  ): InlineKeyboardMarkup {
    const rows: any[][] = [];

    if (currentDay === 'today') {
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

    rows.push([{ text: '« Главное меню', callback_data: 'cmd:menu' }]);
    return { inline_keyboard: rows };
  }

  async handleInlineQuery(queryId: string, queryText: string): Promise<void> {
    const query = queryText.trim();
    if (!query || query.length < 2) {
      await this.tg.answerInlineQuery({ inline_query_id: queryId, results: [], cache_time: 300 });
      return;
    }

    let inst = 'rksi';
    let groups = await this.schedule.searchGroups(inst, query);
    if (groups.length === 0) {
      inst = 'dgtu';
      groups = await this.schedule.searchGroups(inst, query);
    }

    if (groups.length === 0) {
      await this.tg.answerInlineQuery({ inline_query_id: queryId, results: [], cache_time: 60 });
      return;
    }

    const matchedGroup = groups[0];
    const scheduleDays = await this.schedule.fetchGroupSchedule(inst, matchedGroup.id || matchedGroup.name);
    if (!scheduleDays || scheduleDays.length === 0) {
      await this.tg.answerInlineQuery({ inline_query_id: queryId, results: [], cache_time: 60 });
      return;
    }

    const todayStr = getMoscowDateString(0);
    const tomorrowStr = getMoscowDateString(1);

    const todayDay = scheduleDays.find(d => d.date === todayStr) || scheduleDays[0];
    const tomorrowDay = scheduleDays.find(d => d.date === tomorrowStr) || scheduleDays[1] || scheduleDays[0];

    const todayRes = await this.schedule.formatDaySchedule(todayDay, matchedGroup.name, false, this.botUsername, inst);
    const tomorrowRes = await this.schedule.formatDaySchedule(tomorrowDay, matchedGroup.name, false, this.botUsername, inst);

    const results = [
      {
        type: 'article',
        id: `today_${matchedGroup.name}`,
        title: `📅 ${matchedGroup.name} — Сегодня (${todayDay.date})`,
        description: `${todayDay.lessons?.length || 0} пар · Нажмите для отправки`,
        input_message_content: { message_text: todayRes.text, parse_mode: 'HTML' }
      },
      {
        type: 'article',
        id: `tomorrow_${matchedGroup.name}`,
        title: `🗓 ${matchedGroup.name} — Завтра (${tomorrowDay.date})`,
        description: `${tomorrowDay.lessons?.length || 0} пар · Нажмите для отправки`,
        input_message_content: { message_text: tomorrowRes.text, parse_mode: 'HTML' }
      }
    ];

    await this.tg.answerInlineQuery({ inline_query_id: queryId, results, cache_time: 120 });
  }
}

function getMoscowDateString(dayOffset = 0): string {
  const d = new Date(Date.now() + 3 * 3600 * 1000 + dayOffset * 24 * 3600 * 1000);
  return d.toISOString().split('T')[0];
}
