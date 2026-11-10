import { Env } from './types';
import { TelegramApi } from './telegram/api';
import { DatabaseService } from './db/d1';
import { ScheduleClient } from './schedule/client';
import { DeliveryBotClient } from './delivery/client';
import { BotHandlers } from './telegram/handlers';
import { BotCallbacks } from './telegram/callbacks';
import { CronWorker } from './cron/worker';

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    // Health check
    if (url.pathname === '/' || url.pathname === '/health') {
      return new Response(
        JSON.stringify({
          service: 'what-schedule-bot',
          status: 'online',
          version: '2.0.0',
          mode: 'single_app',
          capabilities: ['schedule_bot', 'pusher', 'cron', 'delivery'],
          timestamp: new Date().toISOString()
        }),
        { headers: { 'Content-Type': 'application/json' } }
      );
    }

    // Cron Trigger Endpoint (manual / automated)
    if (url.pathname === '/api/cron/run') {
      const tg = new TelegramApi(env.TELEGRAM_BOT_TOKEN);
      const db = new DatabaseService(env.DB);
      const schedule = new ScheduleClient(env.SCHEDULE_CACHE_BASE_URL);
      const delivery = new DeliveryBotClient(env);
      const worker = new CronWorker(tg, db, schedule, delivery, env);

      const force = url.searchParams.get('force') || undefined;
      const stats = await worker.run(force);
      return new Response(JSON.stringify({ success: true, stats, timestamp: new Date().toISOString() }), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Push Notification API Endpoint (Cloudflare Worker Pusher)
    if (url.pathname === '/api/push' && request.method === 'POST') {
      try {
        const body: any = await request.json();
        const tg = new TelegramApi(env.TELEGRAM_BOT_TOKEN);
        const db = new DatabaseService(env.DB);

        // 1. Single chat push
        if (body.chat_id && body.text) {
          const res = await tg.sendMessage({
            chat_id: Number(body.chat_id),
            text: body.text,
            parse_mode: body.parse_mode || 'HTML',
            reply_markup: body.reply_markup
          });
          return new Response(JSON.stringify({ success: true, result: res, timestamp: new Date().toISOString() }), {
            headers: { 'Content-Type': 'application/json' }
          });
        }

        // 2. Broadcast / Target Group push
        if (body.broadcast || body.group_name || body.institution) {
          const queryInst = body.institution;
          const targetGroup = body.group_name;
          const chats = await db.getSubscribedChatsForReplacements(queryInst);

          let sentCount = 0;
          let failedCount = 0;

          for (const chat of chats) {
            if (targetGroup && chat.group_name !== targetGroup) continue;

            try {
              await tg.sendMessage({
                chat_id: Number(chat.chat_id),
                text: body.text,
                parse_mode: body.parse_mode || 'HTML',
                reply_markup: body.reply_markup
              });
              sentCount++;
            } catch (e) {
              failedCount++;
              console.error('Push error for chat', chat.chat_id, e);
            }
          }

          return new Response(JSON.stringify({
            success: true,
            sent_count: sentCount,
            failed_count: failedCount,
            total_target: chats.length,
            timestamp: new Date().toISOString()
          }), {
            headers: { 'Content-Type': 'application/json' }
          });
        }

        return new Response(JSON.stringify({ error: 'Missing chat_id or text / broadcast flag' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' }
        });
      } catch (err: any) {
        return new Response(JSON.stringify({ error: err.message }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        });
      }
    }

    // Telegram Bot Webhook
    if (url.pathname === '/api/telegram/webhook') {
      if (request.method !== 'POST') {
        return new Response('Method not allowed', { status: 405 });
      }

      const tg = new TelegramApi(env.TELEGRAM_BOT_TOKEN);
      const db = new DatabaseService(env.DB);
      const schedule = new ScheduleClient(env.SCHEDULE_CACHE_BASE_URL);
      const delivery = new DeliveryBotClient(env);
      const handlers = new BotHandlers(tg, db, schedule, delivery, env);
      const callbacks = new BotCallbacks(tg, db, handlers, delivery, schedule);

      try {
        const update: any = await request.json();

        // 1. Inline Queries (@bot ИС-21)
        if (update.inline_query) {
          await handlers.handleInlineQuery(update.inline_query.id, update.inline_query.query);
          return new Response('OK');
        }

        // 2. Callback Queries (buttons tap)
        if (update.callback_query) {
          await callbacks.handleCallback(update.callback_query);
          return new Response('OK');
        }

        // 3. Message Handler
        if (update.message && update.message.text) {
          const msg = update.message;
          const text: string = msg.text.trim();
          const chatId: number = msg.chat.id;
          const chatType: string = msg.chat.type;
          const title = msg.chat.title;

          const parts = text.split(' ');
          const command = parts[0].split('@')[0].toLowerCase();
          const args = parts.slice(1);

          // Delete user trigger command message to keep chat clean (in groups/private)
          if (command.startsWith('/')) {
            ctx.waitUntil(tg.deleteMessage({ chat_id: chatId, message_id: msg.message_id }));
          }

          if (command === '/start') {
            await handlers.handleStart(chatId, msg.from, chatType, title, undefined, args);
          } else if (command === '/today') {
            await handlers.handleToday(chatId);
          } else if (command === '/tomorrow') {
            await handlers.handleTomorrow(chatId);
          } else if (command === '/week') {
            await handlers.handleWeek(chatId);
          } else if (command === '/settings' || command === '/setgroup') {
            await handlers.showInstitutionPicker(chatId);
          } else if (command === '/menu') {
            const chat = await db.getChat(chatId);
            if (chat && chat.group_name) {
              await handlers.showMainScheduleMenu(chatId, chat);
            } else {
              await handlers.showInstitutionPicker(chatId);
            }
          } else {
            // Any text typed by the student is treated as a smart group search!
            await handlers.handleUserTextInput(chatId, text);
          }
        }

        return new Response('OK');
      } catch (err: any) {
        console.error('Webhook error', err);
        return new Response(`Error: ${err.message}`, { status: 200 });
      }
    }

    return new Response(JSON.stringify({ error: 'Not Found' }), {
      status: 404,
      headers: { 'Content-Type': 'application/json' }
    });
  },

  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    const tg = new TelegramApi(env.TELEGRAM_BOT_TOKEN);
    const db = new DatabaseService(env.DB);
    const schedule = new ScheduleClient(env.SCHEDULE_CACHE_BASE_URL);
    const delivery = new DeliveryBotClient(env);
    const worker = new CronWorker(tg, db, schedule, delivery, env);

    ctx.waitUntil(worker.run());
  }
};
