import { DeliveryNotificationItem, DeliveryResponse, Env } from '../types';

export class DeliveryBotClient {
  constructor(private env: Env) {}

  async fetchActiveNotifications(institution: string): Promise<DeliveryNotificationItem[]> {
    try {
      const res = await fetch(this.env.DELIVERY_RAW_URL);
      if (!res.ok) return [];

      const data: DeliveryResponse = await res.json();
      const now = new Date().toISOString();

      return data.items.filter(item => {
        if (item.expires_at && item.expires_at < now) return false;
        if (item.starts_at && item.starts_at > now) return false;

        const targeting = item.targeting;
        if (!targeting) return true;

        // Platform check: must allow telegram or bot
        if (targeting.platforms && targeting.platforms.length > 0) {
          const matchPlatform = targeting.platforms.some(p => ['telegram', 'bot'].includes(p.toLowerCase()));
          if (!matchPlatform) return false;
        }

        // App id check
        if (targeting.app_ids && targeting.app_ids.length > 0) {
          const matchApp = targeting.app_ids.some(id => ['what.schedule.bot', 'what-schedule-bot'].includes(id));
          if (!matchApp) return false;
        }

        // Institution check
        if (targeting.institutions && targeting.institutions.length > 0) {
          if (!targeting.institutions.includes(institution.toLowerCase())) return false;
        }

        return true;
      });
    } catch (e) {
      console.error('Failed to fetch delivery notifications', e);
      return [];
    }
  }

  async submitSurveyResponse(payload: {
    survey_id: string;
    user_id: string;
    app_id: string;
    institution: string;
    answers: { question_id: string; answer: any }[];
  }): Promise<boolean> {
    try {
      const url = this.env.SURVEY_SUBMIT_URL;
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...payload,
          app_version: 'WHAT-Schedule-Bot 1.0',
          submitted_at: new Date().toISOString()
        })
      });
      return res.ok;
    } catch {
      return false;
    }
  }
}
