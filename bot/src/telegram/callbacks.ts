import { TelegramApi } from './api';
import { DatabaseService } from '../db/d1';
import { BotHandlers } from './handlers';
import { ScheduleClient } from '../schedule/client';
import { DeliveryBotClient } from '../delivery/client';

export class BotCallbacks {
  constructor(
    private tg: TelegramApi,
    private db: DatabaseService,
    private handlers: BotHandlers,
    private delivery: DeliveryBotClient,
    private schedule: ScheduleClient
  ) {}

  async handleCallback(callbackQuery: any): Promise<void> {
    const data: string = callbackQuery.data || '';
    const queryId: string = callbackQuery.id;
    const message = callbackQuery.message;
    const chatId = message?.chat?.id;
    const messageId = message?.message_id;

    if (!chatId) return;

    // 1. Institution selection -> opens Hub
    if (data.startsWith('inst_select:')) {
      const instId = data.replace('inst_select:', '');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.handleInstitutionSelect(chatId, instId, messageId);
      return;
    }

    // 2. Hub choices: Group search vs Teacher search vs Courses
    if (data.startsWith('inst_mode:group:')) {
      const instId = data.replace('inst_mode:group:', '');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.promptGroupSearch(chatId, instId, messageId);
      return;
    }

    if (data.startsWith('inst_mode:teacher:')) {
      const instId = data.replace('inst_mode:teacher:', '');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.promptTeacherSearch(chatId, instId, messageId);
      return;
    }

    if (data.startsWith('inst_courses:')) {
      const instId = data.replace('inst_courses:', '');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showCoursesMenu(chatId, instId, messageId);
      return;
    }

    // Course selected (1, 2, 3...)
    if (data.startsWith('course_sel:')) {
      const [, instId, courseNumStr] = data.split(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.handleCourseSelect(chatId, instId, parseInt(courseNumStr, 10), messageId);
      return;
    }

    // Letter filter on course (for DGTU/RINH with 500 groups)
    if (data.startsWith('g_clet:') || data.startsWith('g_course_let:')) {
      const parts = data.split(':');
      const instId = parts[1];
      const courseNumStr = parts[2];
      const letterEncoded = parts[3];
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.handleCourseLetterGroups(
        chatId,
        instId,
        parseInt(courseNumStr, 10),
        decodeURIComponent(letterEncoded),
        messageId
      );
      return;
    }

    // Teacher letter filter
    if (data.startsWith('t_letter:')) {
      const [, instId, letter] = data.split(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.handleTeacherLetter(chatId, instId, letter, messageId);
      return;
    }

    if (data.startsWith('group_search_prompt:')) {
      const instId = data.replace('group_search_prompt:', '');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.promptGroupSearch(chatId, instId, messageId);
      return;
    }

    // 3. Picking group as primary target (compact callback_data <= 20 bytes)
    if (data.startsWith('pick_grp:')) {
      // format: pick_grp:<inst>:<groupId>
      const [, inst, groupId] = data.split(':');
      const groupName = await this.schedule.resolveGroupName(inst, groupId);

      await this.db.setChatTarget(chatId, inst, 'group', groupName, groupId);
      await this.tg.answerCallbackQuery({ callback_query_id: queryId, text: `Группа ${groupName} сохранена` });

      const updatedChat = await this.db.getChat(chatId);
      if (updatedChat) {
        await this.handlers.showMainScheduleMenu(chatId, updatedChat, messageId);
      }
      return;
    }

    // Backwards compatible pick_group
    if (data.startsWith('pick_group:')) {
      const parts = data.split(':');
      const inst = parts[1];
      const groupId = parts[2];
      const groupName = parts.slice(3).join(':') || (await this.schedule.resolveGroupName(inst, groupId));

      await this.db.setChatTarget(chatId, inst, 'group', groupName, groupId);
      await this.tg.answerCallbackQuery({ callback_query_id: queryId, text: `Группа ${groupName} сохранена` });

      const updatedChat = await this.db.getChat(chatId);
      if (updatedChat) {
        await this.handlers.showMainScheduleMenu(chatId, updatedChat, messageId);
      }
      return;
    }

    // 4. Picking teacher as primary target (compact callback_data <= 20 bytes)
    if (data.startsWith('pick_tchr:')) {
      // format: pick_tchr:<inst>:<teacherId>
      const [, inst, teacherId] = data.split(':');
      const teacherName = await this.schedule.getTeacherName(inst, teacherId);

      await this.db.setChatTarget(chatId, inst, 'teacher', teacherName, teacherId);
      await this.tg.answerCallbackQuery({ callback_query_id: queryId, text: `Преподаватель ${teacherName} сохранен` });

      const updatedChat = await this.db.getChat(chatId);
      if (updatedChat) {
        await this.handlers.showMainScheduleMenu(chatId, updatedChat, messageId);
      }
      return;
    }

    // Backwards compatible pick_teacher
    if (data.startsWith('pick_teacher:')) {
      const parts = data.split(':');
      const inst = parts[1];
      const teacherId = parts[2];
      const teacherName = parts.slice(3).join(':') || (await this.schedule.getTeacherName(inst, teacherId));

      await this.db.setChatTarget(chatId, inst, 'teacher', teacherName, teacherId);
      await this.tg.answerCallbackQuery({ callback_query_id: queryId, text: `Преподаватель ${teacherName} сохранен` });

      const updatedChat = await this.db.getChat(chatId);
      if (updatedChat) {
        await this.handlers.showMainScheduleMenu(chatId, updatedChat, messageId);
      }
      return;
    }

    // 5. Viewing teacher schedule
    if (data.startsWith('t_view:')) {
      const [, inst, teacherId] = data.split(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showTeacherSchedule(chatId, inst, teacherId, 0, messageId);
      return;
    }

    if (data.startsWith('t_today:')) {
      const [, inst, teacherId] = data.split(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showTeacherSchedule(chatId, inst, teacherId, 0, messageId);
      return;
    }

    if (data.startsWith('t_tomorrow:')) {
      const [, inst, teacherId] = data.split(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showTeacherSchedule(chatId, inst, teacherId, 1, messageId);
      return;
    }

    if (data.startsWith('t_week:')) {
      const [, inst, teacherId] = data.split(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showTeacherWeekSchedule(chatId, inst, teacherId, messageId);
      return;
    }

    // 6. Viewing group schedule (quick view / switch)
    if (data.startsWith('g_view:')) {
      const [, inst, ...grpParts] = data.split(':');
      const groupIdOrName = grpParts.join(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showGroupSchedule(chatId, inst, groupIdOrName, 0, messageId);
      return;
    }

    if (data.startsWith('g_today:')) {
      const [, inst, ...grpParts] = data.split(':');
      const groupIdOrName = grpParts.join(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showGroupSchedule(chatId, inst, groupIdOrName, 0, messageId);
      return;
    }

    if (data.startsWith('g_tomorrow:')) {
      const [, inst, ...grpParts] = data.split(':');
      const groupIdOrName = grpParts.join(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showGroupSchedule(chatId, inst, groupIdOrName, 1, messageId);
      return;
    }

    if (data.startsWith('g_week:')) {
      const [, inst, ...grpParts] = data.split(':');
      const groupIdOrName = grpParts.join(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showGroupWeekSchedule(chatId, inst, groupIdOrName, messageId);
      return;
    }

    // 7. Navigation & Switch Hub
    if (data === 'cmd:menu') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      const chat = await this.db.getChat(chatId);
      if (chat && chat.group_name) {
        await this.handlers.showMainScheduleMenu(chatId, chat, messageId);
      } else {
        await this.handlers.showInstitutionPicker(chatId, messageId);
      }
      return;
    }

    if (data === 'menu:change_hub') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      const chat = await this.db.getChat(chatId);
      const inst = chat?.institution || 'rksi';
      await this.handlers.handleInstitutionSelect(chatId, inst, messageId);
      return;
    }

    if (data === 'menu:change_inst') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.showInstitutionPicker(chatId, messageId);
      return;
    }

    // 8. Schedule commands
    if (data === 'cmd:today') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.handleToday(chatId, messageId);
      return;
    }

    if (data === 'cmd:tomorrow') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.handleTomorrow(chatId, messageId);
      return;
    }

    if (data === 'cmd:week') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handlers.handleWeek(chatId, messageId);
      return;
    }

    // 9. Toggle replacements notification
    if (data === 'toggle:replacements') {
      const chat = await this.db.getChat(chatId);
      const newStatus = (chat?.notify_replacements === 1) ? 0 : 1;
      await this.db.upsertChat({ chat_id: String(chatId), notify_replacements: newStatus });

      await this.tg.answerCallbackQuery({
        callback_query_id: queryId,
        text: newStatus === 1 ? 'Уведомления о заменах включены' : 'Уведомления о заменах выключены'
      });

      const updatedChat = await this.db.getChat(chatId);
      if (updatedChat) {
        await this.handlers.showMainScheduleMenu(chatId, updatedChat, messageId);
      }
      return;
    }

    if (data === 'menu:notify_time' || data === 'toggle:tomorrow_notify') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      const chat = await this.db.getChat(chatId);
      if (chat) {
        await this.handlers.showNotifyTimePicker(chatId, chat, messageId);
      }
      return;
    }

    if (data === 'menu:notify_hours') {
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      const chat = await this.db.getChat(chatId);
      if (chat) {
        await this.handlers.showHourlyPicker(chatId, chat, messageId);
      }
      return;
    }

    if (data.startsWith('set_notify:')) {
      const mode = data.slice('set_notify:'.length);
      await this.db.upsertChat({ chat_id: String(chatId), notify_tomorrow_time: mode });

      const labels: Record<string, string> = {
        '07:00': 'Рассылка установлена на утро (07:00)',
        '19:00': 'Рассылка установлена на вечер (19:00)',
        morning: 'Рассылка установлена на утро (07:00)',
        evening: 'Рассылка установлена на вечер (19:00)',
        both: 'Рассылка установлена на утро и вечер',
        off: 'Ежедневная рассылка отключена'
      };

      const alertText = labels[mode] || `Рассылка установлена на ${mode} МСК`;
      await this.tg.answerCallbackQuery({
        callback_query_id: queryId,
        text: alertText
      });

      const updatedChat = await this.db.getChat(chatId);
      if (updatedChat) {
        if (mode.includes(':') && mode !== '07:00' && mode !== '19:00') {
          await this.handlers.showHourlyPicker(chatId, updatedChat, messageId);
        } else {
          await this.handlers.showNotifyTimePicker(chatId, updatedChat, messageId);
        }
      }
      return;
    }

    // 10. Survey execution
    if (data.startsWith('survey_start:')) {
      const surveyId = data.replace('survey_start:', '');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.startSurvey(chatId, surveyId);
      return;
    }

    if (data.startsWith('survey_ans:')) {
      const [, surveyId, qIndexStr, optId] = data.split(':');
      await this.tg.answerCallbackQuery({ callback_query_id: queryId });
      await this.handleSurveyAnswer(chatId, surveyId, parseInt(qIndexStr, 10), optId, messageId);
      return;
    }
  }

  private async startSurvey(chatId: number, surveyId: string) {
    const chat = await this.db.getChat(chatId);
    const notifications = await this.delivery.fetchActiveNotifications(chat?.institution || 'rksi');
    const item = notifications.find(n => n.survey?.survey_id === surveyId);
    if (!item || !item.survey || item.survey.questions.length === 0) {
      await this.tg.sendMessage({ chat_id: chatId, text: '⚠️ Опрос завершён или недоступен.' });
      return;
    }

    await this.db.saveSurveySession(String(chatId), surveyId, 0, '{}', 'in_progress');
    await this.renderQuestion(chatId, item.survey, 0);
  }

  private async handleSurveyAnswer(chatId: number, surveyId: string, qIndex: number, optId: string, messageId?: number) {
    const chat = await this.db.getChat(chatId);
    const notifications = await this.delivery.fetchActiveNotifications(chat?.institution || 'rksi');
    const item = notifications.find(n => n.survey?.survey_id === surveyId);
    if (!item || !item.survey) return;

    const survey = item.survey;
    const session = await this.db.getSurveySession(String(chatId), surveyId);
    const answers = session ? JSON.parse(session.answers_json || '{}') : {};

    const currentQ = survey.questions[qIndex];
    if (currentQ) {
      answers[currentQ.id] = optId;
    }

    const nextIndex = qIndex + 1;
    if (nextIndex < survey.questions.length) {
      await this.db.saveSurveySession(String(chatId), surveyId, nextIndex, JSON.stringify(answers), 'in_progress');
      await this.renderQuestion(chatId, survey, nextIndex, messageId);
    } else {
      await this.db.saveSurveySession(String(chatId), surveyId, nextIndex, JSON.stringify(answers), 'completed');

      const formattedAnswers = Object.entries(answers).map(([qid, val]) => ({
        question_id: qid,
        answer: val
      }));

      await this.delivery.submitSurveyResponse({
        survey_id: surveyId,
        user_id: `WHAT-BOT-${chatId}`,
        app_id: 'what.schedule.bot',
        institution: chat?.institution || 'rksi',
        answers: formattedAnswers
      });

      const finishText = `✅ <b>Опрос пройден.</b>\nВаши ответы сохранены. Спасибо за обратную связь!`;
      if (messageId) {
        await this.tg.editMessageText({ chat_id: chatId, message_id: messageId, text: finishText, parse_mode: 'HTML' });
      } else {
        await this.tg.sendMessage({ chat_id: chatId, text: finishText, parse_mode: 'HTML' });
      }
    }
  }

  private async renderQuestion(chatId: number, survey: any, qIndex: number, messageId?: number) {
    const q = survey.questions[qIndex];
    const totalQ = survey.questions.length;

    const lines = [
      `📋 <b>Опрос: ${survey.survey_id}</b> (${qIndex + 1}/${totalQ})`,
      '',
      `❓ <b>${q.title}</b>`,
      ''
    ];

    const keyboard = {
      inline_keyboard: (q.options || []).map((opt: any) => [
        { text: opt.title, callback_data: `survey_ans:${survey.survey_id}:${qIndex}:${opt.id}` }
      ])
    };

    const msgText = lines.join('\n');

    if (messageId) {
      await this.tg.editMessageText({
        chat_id: chatId,
        message_id: messageId,
        text: msgText,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    } else {
      await this.tg.sendMessage({
        chat_id: chatId,
        text: msgText,
        parse_mode: 'HTML',
        reply_markup: keyboard
      });
    }
  }
}
