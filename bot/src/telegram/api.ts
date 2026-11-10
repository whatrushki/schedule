export interface InlineKeyboardButton {
  text: string;
  callback_data?: string;
  url?: string;
  switch_inline_query_current_chat?: string;
}

export interface InlineKeyboardMarkup {
  inline_keyboard: InlineKeyboardButton[][];
}

export class TelegramApi {
  private baseUrl: string;

  constructor(token: string) {
    this.baseUrl = `https://api.telegram.org/bot${token}`;
  }

  async sendMessage(params: {
    chat_id: string | number;
    text: string;
    parse_mode?: 'HTML' | 'Markdown' | 'MarkdownV2';
    reply_markup?: InlineKeyboardMarkup;
    disable_web_page_preview?: boolean;
  }): Promise<{ ok: boolean; result?: any; description?: string }> {
    const res = await fetch(`${this.baseUrl}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...params,
        disable_web_page_preview: params.disable_web_page_preview ?? true
      })
    });
    return res.json();
  }

  async editMessageText(params: {
    chat_id: string | number;
    message_id: number;
    text: string;
    parse_mode?: 'HTML' | 'Markdown';
    reply_markup?: InlineKeyboardMarkup;
  }): Promise<{ ok: boolean; result?: any; description?: string }> {
    try {
      const res = await fetch(`${this.baseUrl}/editMessageText`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params)
      });
      return await res.json();
    } catch (e: any) {
      return { ok: false, description: e?.message };
    }
  }

  async deleteMessage(params: {
    chat_id: string | number;
    message_id: number;
  }): Promise<{ ok: boolean; description?: string }> {
    try {
      const res = await fetch(`${this.baseUrl}/deleteMessage`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params)
      });
      return await res.json();
    } catch (_) {
      return { ok: false };
    }
  }

  async answerCallbackQuery(params: {
    callback_query_id: string;
    text?: string;
    show_alert?: boolean;
  }): Promise<{ ok: boolean }> {
    const res = await fetch(`${this.baseUrl}/answerCallbackQuery`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params)
    });
    return res.json();
  }

  async answerInlineQuery(params: {
    inline_query_id: string;
    results: any[];
    cache_time?: number;
    is_personal?: boolean;
  }): Promise<{ ok: boolean }> {
    const res = await fetch(`${this.baseUrl}/answerInlineQuery`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params)
    });
    return res.json();
  }

  async setMyCommands(commands: { command: string; description: string }[]): Promise<void> {
    await fetch(`${this.baseUrl}/setMyCommands`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ commands })
    });
  }
}
