import { DayDto, GroupMetaDto, InstitutionInfo, LessonDto, OtUnitDto, TeacherMetaDto } from '../types';

export const INSTITUTIONS: InstitutionInfo[] = [
  { id: 'rksi', shortName: 'РКСИ', name: 'Колледж связи и информатики', emoji: '🏛' },
  { id: 'dgtu', shortName: 'ДГТУ', name: 'Донской государственный тех. университет', emoji: '🎓' },
  { id: 'sfedu', shortName: 'ЮФУ', name: 'Южный федеральный университет', emoji: '📚' },
  { id: 'rinh', shortName: 'РИНХ', name: 'РГЭУ (РИНХ)', emoji: '🏢' },
  { id: 'iubip', shortName: 'ИУБиП', name: 'Южный университет (ИУБиП)', emoji: '🏫' },
  { id: 'tvgu', shortName: 'ТвГУ', name: 'Тверской государственный университет', emoji: '🎓' }
];

export interface FormattedScheduleResult {
  text: string;
  referencedTeachers: { id: string; name: string }[];
  referencedGroups: { id: string; name: string }[];
}

export class ScheduleClient {
  private rawCdnBase = 'https://raw.githubusercontent.com/whatrushki/WHAT-Schedule-android/gh-pages/schedule';
  private teachersCache: Map<string, { list: TeacherMetaDto[]; keyMap: Map<string, string>; timestamp: number }> = new Map();
  private groupsCache: Map<string, { list: GroupMetaDto[]; timestamp: number }> = new Map();

  constructor(private fallbackBaseUrl: string) {}

  private getUrls(inst: string, path: string): string[] {
    const list: string[] = [
      `https://raw.githubusercontent.com/whatrushki/WHAT-Schedule-android/gh-pages/schedule/${inst}/${path}`,
      `https://raw.githubusercontent.com/whatrushki/schedule/gh-pages/schedule/${inst}/${path}`,
      `https://cdn.jsdelivr.net/gh/whatrushki/schedule@gh-pages/schedule/${inst}/${path}`
    ];
    if (this.fallbackBaseUrl && !this.fallbackBaseUrl.includes('whatrushki.github.io')) {
      list.push(`${this.fallbackBaseUrl}/${inst}/${path}`);
    }
    return list;
  }

  // --- GROUPS ---
  async fetchGroups(institution: string): Promise<GroupMetaDto[]> {
    const inst = institution.toLowerCase();
    const cached = this.groupsCache.get(inst);
    if (cached && Date.now() - cached.timestamp < 15 * 60 * 1000) {
      return cached.list;
    }

    const urls = this.getUrls(inst, 'groups.json');

    for (const url of urls) {
      try {
        const res = await fetch(url, { headers: { 'Cache-Control': 'no-cache', 'User-Agent': 'WHAT-Schedule-Bot/2.0' } });
        if (res.ok) {
          const list: any[] = await res.json();
          const parsed = list.map(item => ({
            id: String(item.id || item.name),
            name: String(item.name || item.id),
            course: typeof item.course === 'number' ? item.course : undefined
          }));
          this.groupsCache.set(inst, { list: parsed, timestamp: Date.now() });
          return parsed;
        }
      } catch (_) {}
    }
    return [];
  }

  async searchGroups(institution: string, query: string): Promise<GroupMetaDto[]> {
    const clean = query.trim().toLowerCase();
    if (!clean) return [];

    const tokens = clean.split(/\s+/).filter(Boolean);
    const all = await this.fetchGroups(institution);

    return all.filter(g => {
      const gName = g.name.toLowerCase();
      const gId = g.id.toLowerCase();
      // Match if all search tokens appear in name or id
      return tokens.every(tok => gName.includes(tok) || gId.includes(tok));
    });
  }

  async resolveGroupId(institution: string, groupIdOrName: string): Promise<string> {
    const clean = groupIdOrName.trim();
    const all = await this.fetchGroups(institution);
    const byId = all.find(g => g.id.toLowerCase() === clean.toLowerCase());
    if (byId) return byId.id;

    const byName = all.find(g => g.name.toLowerCase() === clean.toLowerCase());
    if (byName) return byName.id;

    return clean;
  }

  async resolveGroupName(institution: string, groupIdOrName: string): Promise<string> {
    const clean = groupIdOrName.trim();
    const all = await this.fetchGroups(institution);
    const byId = all.find(g => g.id.toLowerCase() === clean.toLowerCase());
    if (byId) return byId.name;

    const byName = all.find(g => g.name.toLowerCase() === clean.toLowerCase());
    if (byName) return byName.name;

    return clean;
  }

  async fetchGroupSchedule(institution: string, groupIdOrName: string): Promise<DayDto[] | null> {
    const inst = institution.toLowerCase();
    const cleanKey = groupIdOrName.trim();

    // 1. Try direct key
    let data = await this.tryFetchGroupJson(inst, encodeURIComponent(cleanKey));
    if (data) return data;

    // 2. Resolve to group ID from groups list (for DGTU/SFEDU where name != id)
    const resolvedId = await this.resolveGroupId(inst, cleanKey);
    if (resolvedId && resolvedId !== cleanKey) {
      data = await this.tryFetchGroupJson(inst, encodeURIComponent(resolvedId));
      if (data) return data;
    }

    return null;
  }

  private async tryFetchGroupJson(inst: string, safeKey: string): Promise<DayDto[] | null> {
    const urls = this.getUrls(inst, `groups/${safeKey}.json`);

    for (const url of urls) {
      try {
        const res = await fetch(url, { headers: { 'Cache-Control': 'no-cache', 'User-Agent': 'WHAT-Schedule-Bot/2.0' } });
        if (res.ok) {
          const data: any = await res.json();
          if (Array.isArray(data)) return data as DayDto[];
          if (data && Array.isArray(data.days)) return data.days as DayDto[];
        }
      } catch (_) {}
    }
    return null;
  }

  // --- TEACHERS ---
  async fetchTeachers(institution: string): Promise<TeacherMetaDto[]> {
    const inst = institution.toLowerCase();
    const cached = this.teachersCache.get(inst);
    if (cached && Date.now() - cached.timestamp < 30 * 60 * 1000) {
      return cached.list;
    }

    const urls = this.getUrls(inst, 'teachers.json');

    for (const url of urls) {
      try {
        const res = await fetch(url, { headers: { 'Cache-Control': 'no-cache', 'User-Agent': 'WHAT-Schedule-Bot/2.0' } });
        if (res.ok) {
          const list: any[] = await res.json();
          const parsed: TeacherMetaDto[] = list.map(item => ({
            id: String(item.id || item.name),
            name: String(item.name || item.id)
          }));

          const keyMap = new Map<string, string>();
          const surnameCounts = new Map<string, number>();

          for (const t of parsed) {
            const fullKey = normalizeTeacherKey(t.name);
            if (fullKey) keyMap.set(fullKey, t.id);
            keyMap.set(t.id, t.id);

            const parts = t.name.trim().split(/\s+/).filter(Boolean);
            if (parts.length > 0) {
              const surname = parts[0].toLowerCase();
              surnameCounts.set(surname, (surnameCounts.get(surname) || 0) + 1);
            }
          }

          // If surname is unique across institution, allow lookup by surname
          for (const t of parsed) {
            const parts = t.name.trim().split(/\s+/).filter(Boolean);
            if (parts.length > 0) {
              const surname = parts[0].toLowerCase();
              if (surnameCounts.get(surname) === 1) {
                keyMap.set(surname, t.id);
              }
            }
          }

          this.teachersCache.set(inst, { list: parsed, keyMap, timestamp: Date.now() });
          return parsed;
        }
      } catch (_) {}
    }
    return [];
  }

  async searchTeachers(institution: string, query: string): Promise<TeacherMetaDto[]> {
    const clean = query.trim().toLowerCase();
    if (!clean) return [];

    const tokens = clean.split(/\s+/).filter(Boolean);
    const all = await this.fetchTeachers(institution);

    return all.filter(t => {
      const tName = t.name.toLowerCase();
      const tId = t.id.toLowerCase();
      return tokens.every(tok => tName.includes(tok) || tId.includes(tok));
    });
  }

  async getTeacherName(institution: string, teacherId: string): Promise<string> {
    const all = await this.fetchTeachers(institution);
    const t = all.find(item => item.id === teacherId);
    return t?.name || teacherId;
  }

  async fetchTeacherSchedule(institution: string, teacherIdOrName: string): Promise<DayDto[] | null> {
    const inst = institution.toLowerCase();
    const cleanKey = teacherIdOrName.trim();

    // 1. Direct fetch
    let data = await this.tryFetchTeacherJson(inst, encodeURIComponent(cleanKey));
    if (data) return data;

    // 2. Resolve teacher name -> id
    const teacherId = await this.getTeacherId(inst, cleanKey);
    if (teacherId && teacherId !== cleanKey) {
      data = await this.tryFetchTeacherJson(inst, encodeURIComponent(teacherId));
      if (data) return data;
    }

    return null;
  }

  private async tryFetchTeacherJson(inst: string, safeKey: string): Promise<DayDto[] | null> {
    const urls = this.getUrls(inst, `teachers/${safeKey}.json`);

    for (const url of urls) {
      try {
        const res = await fetch(url, { headers: { 'Cache-Control': 'no-cache', 'User-Agent': 'WHAT-Schedule-Bot/2.0' } });
        if (res.ok) {
          const data: any = await res.json();
          if (Array.isArray(data)) return data as DayDto[];
          if (data && Array.isArray(data.days)) return data.days as DayDto[];
        }
      } catch (_) {}
    }
    return null;
  }

  async getTeacherId(institution: string, teacherName: string): Promise<string | null> {
    if (!teacherName) return null;
    const inst = institution.toLowerCase();
    let cached = this.teachersCache.get(inst);
    if (!cached) {
      await this.fetchTeachers(institution);
      cached = this.teachersCache.get(inst);
    }
    if (!cached) return null;

    const key = normalizeTeacherKey(teacherName);
    return cached.keyMap.get(key) || cached.keyMap.get(teacherName.trim().split(/\s+/)[0].toLowerCase()) || null;
  }

  renderGroupLink(group: string | null | undefined, institution: string, botUsername: string): string {
    if (!group || group === '-' || group.trim().length === 0) return '–';
    const cleanGroup = group.trim();
    if (botUsername) {
      const deepLink = `https://t.me/${botUsername}?start=g_${institution}_${toHex(cleanGroup)}`;
      return `<a href="${deepLink}">${escapeHtml(cleanGroup)}</a>`;
    }
    return escapeHtml(cleanGroup);
  }

  async formatTeacherLink(teacher: string | null | undefined, institution: string, botUsername: string): Promise<string> {
    if (!teacher || teacher === '-' || teacher.trim().length === 0) return '–';
    const cleanTeacher = teacher.trim();
    const teacherId = await this.getTeacherId(institution, cleanTeacher);

    if (teacherId && botUsername) {
      const deepLink = `https://t.me/${botUsername}?start=t_${institution}_${toHex(teacherId)}`;
      return `<a href="${deepLink}">${escapeHtml(cleanTeacher)}</a>`;
    }

    return escapeHtml(cleanTeacher);
  }

  // --- SCHEDULE FORMATTING ---
  async formatDaySchedule(
    day: DayDto,
    targetName: string,
    isTeacher = false,
    botUsername = 'whatrushki_schedule_bot',
    institution = 'rksi',
    customHeader?: string
  ): Promise<FormattedScheduleResult> {
    const dateFormatted = formatRuDate(day.date);
    const dayOfWeekShort = getDayOfWeekRuShort(day.date);
    const header = customHeader || `<blockquote>${dateFormatted} (${dayOfWeekShort})</blockquote>`;

    const referencedTeachers: { id: string; name: string }[] = [];
    const referencedGroupsRaw = new Set<string>();

    if (!day.lessons || day.lessons.length === 0) {
      return {
        text: `${header}\n\nПары отсутствуют`,
        referencedTeachers: [],
        referencedGroups: []
      };
    }

    // Group lessons into time slots (pairing sub-groups while separating distinct times)
    interface ScheduleSlot {
      slotKey: string;
      number: number;
      isClassHour: boolean;
      startTime: string;
      endTime: string;
      lessons: LessonDto[];
    }

    const slotMap = new Map<string, ScheduleSlot>();
    for (const l of day.lessons) {
      const isClassHour = (l.subject && l.subject.toLowerCase().includes('классный час')) ||
        (l.type && l.type.toLowerCase().includes('class_hour')) ||
        (l.number === 0 && (!l.subject || l.subject.toLowerCase().includes('классн') || l.subject.trim() === ''));

      // If class hour, isolate by its start time; otherwise group by pair number
      const slotKey = isClassHour
        ? `class_hour_${l.startTime || '0'}`
        : `pair_${l.number}_${l.startTime || ''}`;

      let slot = slotMap.get(slotKey);
      if (!slot) {
        slot = {
          slotKey,
          number: l.number,
          isClassHour: !!isClassHour,
          startTime: l.startTime || '',
          endTime: l.endTime || '',
          lessons: []
        };
        slotMap.set(slotKey, slot);
      }
      slot.lessons.push(l);
      if (!slot.startTime && l.startTime) slot.startTime = l.startTime;
      if (!slot.endTime && l.endTime) slot.endTime = l.endTime;
    }

    // Sort slots chronologically by start time!
    const sortedSlots = Array.from(slotMap.values()).sort((a, b) => {
      // 1. Primary: compare start times if both available
      if (a.startTime && b.startTime) {
        const timeCmp = a.startTime.localeCompare(b.startTime);
        if (timeCmp !== 0) return timeCmp;
      }
      if (a.startTime && !b.startTime) return -1;
      if (!a.startTime && b.startTime) return 1;

      // 2. Secondary: compare pair numbers
      if (a.number !== b.number) {
        if (a.isClassHour && !b.isClassHour && b.number > 0) return 1;
        if (!a.isClassHour && b.isClassHour && a.number > 0) return -1;
        return a.number - b.number;
      }
      return 0;
    });

    const blocks: string[] = [header, ''];

    for (const slot of sortedSlots) {
      const lessons = slot.lessons;
      const timeStr = slot.startTime && slot.endTime
        ? `${slot.startTime}–${slot.endTime}`
        : slot.startTime || '';

      const isReplacement = lessons.some(l => l.state === 'CHANGED' || l.state === 'REPLACEMENT');
      const isCancelled = lessons.every(l => l.state === 'CANCELLED');

      let statusTag = '';
      if (isReplacement) statusTag = '  (♻️)';
      if (isCancelled) statusTag = '  (❌ Отменено)';

      let slotHeader: string;
      if (slot.isClassHour) {
        slotHeader = `🌱 <b>Классный час</b> <i>${timeStr}${statusTag}</i>`.trim();
      } else if (slot.number === 0) {
        slotHeader = `🌱 <b>0 пара</b> <i>${timeStr}${statusTag}</i>`.trim();
      } else {
        slotHeader = `🌱 <b>${slot.number} пара</b> <i>${timeStr}${statusTag}</i>`.trim();
      }

      // Collect all distinct subjects across this slot
      const distinctSubjects = Array.from(new Set(lessons.map(l => (l.subject || 'Предмет не указан').trim())));

      if (distinctSubjects.length === 1) {
        const subject = distinctSubjects[0];
        const isOnlyClassHourTitle = slot.isClassHour && (subject.toLowerCase() === 'классный час' || subject === 'Предмет не указан');
        const lines: string[] = isOnlyClassHourTitle ? [slotHeader] : [slotHeader, `<b>${escapeHtml(subject)}</b>`];

        // Flatten all units
        const units: OtUnitDto[] = [];
        for (const l of lessons) {
          if (l.otUnits && l.otUnits.length > 0) {
            units.push(...l.otUnits);
          }
        }

        if (units.length === 0) {
          if (!isOnlyClassHourTitle) lines.push('Ауд. –');
        } else if (units.length === 1) {
          const u = units[0];
          if (isTeacher) {
            if (u.group && u.group.trim().length > 0 && u.group !== '-') {
              referencedGroupsRaw.add(u.group.trim());
            }
            const groupDisplay = this.renderGroupLink(u.group, institution, botUsername);
            lines.push(`Группа ${groupDisplay}`);
            lines.push(`Ауд. ${escapeHtml(u.room || '–')}`);
          } else {
            if (u.teacher && u.teacher.trim().length > 0 && u.teacher !== '-') {
              const tId = await this.getTeacherId(institution, u.teacher.trim());
              if (tId && !referencedTeachers.some(t => t.id === tId)) {
                referencedTeachers.push({ id: tId, name: u.teacher.trim() });
              }
            }
            const teacherDisplay = await this.formatTeacherLink(u.teacher, institution, botUsername);
            if (teacherDisplay) lines.push(teacherDisplay);
            lines.push(`Ауд. ${escapeHtml(u.room || '–')}`);
          }
        } else {
          // Multiple units for same subject
          for (const u of units) {
            const sub = formatSubgroup(u.additional);
            if (isTeacher) {
              if (u.group && u.group.trim().length > 0 && u.group !== '-') {
                referencedGroupsRaw.add(u.group.trim());
              }
              const groupDisplay = this.renderGroupLink(u.group, institution, botUsername);
              lines.push(`— Ауд. ${escapeHtml(u.room || '–')} — ${groupDisplay}${sub}`);
            } else {
              if (u.teacher && u.teacher.trim().length > 0 && u.teacher !== '-') {
                const tId = await this.getTeacherId(institution, u.teacher.trim());
                if (tId && !referencedTeachers.some(t => t.id === tId)) {
                  referencedTeachers.push({ id: tId, name: u.teacher.trim() });
                }
              }
              const teacherDisplay = await this.formatTeacherLink(u.teacher, institution, botUsername);
              lines.push(`— Ауд. ${escapeHtml(u.room || '–')} — ${teacherDisplay}${sub}`);
            }
          }
        }

        blocks.push(lines.join('\n'));
      } else {
        // Multiple different subjects in one slot (collision)
        const lines: string[] = [slotHeader];

        for (const l of lessons) {
          const subjName = escapeHtml(l.subject || 'Предмет не указан');
          const units = (l.otUnits && l.otUnits.length > 0) ? l.otUnits : [{} as OtUnitDto];

          for (const u of units) {
            const sub = formatSubgroup(u.additional);
            const roomStr = escapeHtml(u.room || '–');

            if (isTeacher) {
              if (u.group && u.group.trim().length > 0 && u.group !== '-') {
                referencedGroupsRaw.add(u.group.trim());
              }
              const grp = this.renderGroupLink(u.group, institution, botUsername);
              lines.push(`• <b>${subjName}</b> — Ауд. ${roomStr} — ${grp}${sub}`);
            } else {
              if (u.teacher && u.teacher.trim().length > 0 && u.teacher !== '-') {
                const tId = await this.getTeacherId(institution, u.teacher.trim());
                if (tId && !referencedTeachers.some(t => t.id === tId)) {
                  referencedTeachers.push({ id: tId, name: u.teacher.trim() });
                }
              }
              const teacherDisplay = await this.formatTeacherLink(u.teacher, institution, botUsername);
              lines.push(`• <b>${subjName}</b> — Ауд. ${roomStr} — ${teacherDisplay}${sub}`);
            }
          }
        }

        blocks.push(lines.join('\n'));
      }

      blocks.push('');
    }

    // Resolve referenced groups to { id, name }
    const referencedGroups: { id: string; name: string }[] = [];
    for (const rawName of referencedGroupsRaw) {
      const gId = await this.resolveGroupId(institution, rawName);
      referencedGroups.push({ id: gId, name: rawName });
    }

    return {
      text: blocks.join('\n').trim(),
      referencedTeachers,
      referencedGroups
    };
  }

  async formatWeekSchedule(
    days: DayDto[],
    targetName: string,
    isTeacher = false,
    botUsername = 'whatrushki_schedule_bot',
    institution = 'rksi'
  ): Promise<string> {
    if (!days || days.length === 0) {
      return `<blockquote>${escapeHtml(targetName)}</blockquote>\n\nРасписание на текущую неделю не найдено.`;
    }

    const dayTexts: string[] = [];
    for (const d of days) {
      const res = await this.formatDaySchedule(d, targetName, isTeacher, botUsername, institution);
      dayTexts.push(res.text);
    }

    return dayTexts.join('\n\n───────────────\n\n');
  }
}

export function escapeHtml(text: string): string {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

export function toHex(str: string): string {
  const enc = new TextEncoder().encode(str);
  let hex = '';
  for (let i = 0; i < enc.length; i++) {
    hex += enc[i].toString(16).padStart(2, '0');
  }
  return hex;
}

export function fromHex(hex: string): string {
  try {
    if (!/^[0-9a-fA-F]+$/.test(hex) || hex.length % 2 !== 0) {
      return hex;
    }
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < hex.length; i += 2) {
      bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16);
    }
    return new TextDecoder().decode(bytes);
  } catch (_) {
    return hex;
  }
}

export function normalizeTeacherKey(name: string): string {
  const cleaned = name.trim()
    .replace(/\u00A0/g, ' ')
    .replace(/c/gi, 'с')
    .replace(/a/gi, 'а')
    .replace(/e/gi, 'е')
    .replace(/o/gi, 'о')
    .replace(/p/gi, 'р')
    .replace(/x/gi, 'х')
    .trim();
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '';
  const surname = parts[0].toLowerCase();
  if (parts.length === 1) return surname;
  const rest = parts.slice(1).join(' ');
  const words = rest.split(/[\s.]+/).filter(w => w.trim().length > 0);
  const initials = words.map(w => w[0].toLowerCase()).join('');
  return surname + initials;
}

export function formatRuDate(dateStr: string): string {
  const parts = dateStr.split('-');
  const months = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
  const mIndex = parseInt(parts[1], 10) - 1;
  return `${parseInt(parts[2], 10)} ${months[mIndex] || parts[1]}`;
}

export function getDayOfWeekRuShort(dateStr: string): string {
  const d = new Date(dateStr + 'T12:00:00Z');
  const daysShort = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
  return daysShort[d.getUTCDay()] || '';
}

function formatSubgroup(subgroup?: string | null): string {
  if (!subgroup) return '';
  const clean = subgroup.trim();
  if (!clean || clean === '0') return '';
  if (clean.includes('подгр') || clean.includes('гр')) {
    return ` (${clean})`;
  }
  return ` (${clean} подгр.)`;
}
