export interface Env {
  DB: D1Database;
  TELEGRAM_BOT_TOKEN: string;
  ENVIRONMENT?: string;
  SCHEDULE_CACHE_BASE_URL: string;
  DELIVERY_RAW_URL: string;
  SURVEY_SUBMIT_URL: string;
  OWNER_CHAT_ID?: string;
}

export interface ChatConfig {
  chat_id: string;
  chat_type: string;
  title?: string;
  institution: string;
  target_type?: 'group' | 'teacher';
  group_name?: string;
  group_id?: string;
  notify_replacements: number;
  notify_tomorrow_time?: string;
  last_replacements_hash?: string;
  last_daily_sent_date?: string;
  pending_action?: string; // e.g. 'search_group:dgtu'
  created_at: string;
  updated_at: string;
}

export interface TeacherMetaDto {
  id: string;
  name: string;
}

export interface OtUnitDto {
  teacher?: string | null;
  group?: string | null;
  room?: string | null;
  additional?: string | null;
  onlineUrl?: string | null;
  subject?: string | null;
}

export interface LessonDto {
  date?: string;
  number: number;
  startTime?: string | null;
  endTime?: string | null;
  subject?: string | null;
  otUnits?: OtUnitDto[];
  type?: string | null;
  state?: string | null;
}

export interface DayDto {
  date: string;
  scheduleType?: string;
  lessons: LessonDto[];
}

export interface GroupMetaDto {
  id: string;
  name: string;
  course?: number;
}

export interface InstitutionInfo {
  id: string;
  name: string;
  shortName: string;
  emoji: string;
}

export interface DeliveryNotificationItem {
  id: string;
  type: 'banner' | 'dialog' | 'push' | 'survey';
  priority?: 'low' | 'normal' | 'high' | 'critical';
  title: string;
  message: string;
  action_title?: string;
  action_url?: string;
  starts_at?: string;
  expires_at: string;
  targeting?: {
    app_ids?: string[];
    platforms?: string[];
    institutions?: string[];
  };
  survey?: {
    survey_id: string;
    submit_url?: string;
    questions: {
      id: string;
      title: string;
      type: 'single_choice' | 'multi_choice' | 'rating' | 'text';
      options?: { id: string; title: string }[];
    }[];
  };
}

export interface DeliveryResponse {
  schema_version: number;
  items: DeliveryNotificationItem[];
}
