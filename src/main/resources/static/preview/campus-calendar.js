const campusCalendarToday = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(new Date());
const campusCalendar = {
  month: campusCalendarToday.slice(0, 7),
  category: '전체',
  day: '',
  school: null,
};
let campusCalendarRequest;
const calendarDateFormat = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'UTC',
  month: 'long',
  day: 'numeric',
  weekday: 'short',
});

function calendarDate(iso) {
  return new Date(iso + 'T00:00:00Z');
}

function calendarButton(text, label, action) {
  const button = el('button', '', text);
  button.type = 'button';
  button.setAttribute('aria-label', label);
  button.onclick = action;
  return button;
}

function calendarEventRange(event) {
  const end = event.end || event.start;
  const range = calendarDateFormat.format(calendarDate(event.start));
  if (end === event.start) return range;
  const endYear = end.slice(0, 4) !== event.start.slice(0, 4) ? end.slice(0, 4) + '년 ' : '';
  return range + ' – ' + endYear + calendarDateFormat.format(calendarDate(end));
}

function calendarMonthEvents(events, month) {
  return events.filter(
    (event) => event.start.slice(0, 7) <= month && (event.end || event.start).slice(0, 7) >= month,
  );
}

function nearestCalendarMonth(events, anchor) {
  const next = events.find((event) => (event.end || event.start) >= anchor);
  const last = events.filter((event) => (event.end || event.start) < anchor).at(-1);
  if (!next && !last) return null;
  if (!last) return next.start.slice(0, 7);
  if (!next) return (last.end || last.start).slice(0, 7);
  const past = last.end || last.start;
  return calendarDate(anchor) - calendarDate(past) < calendarDate(next.start) - calendarDate(anchor)
    ? past.slice(0, 7)
    : next.start.slice(0, 7);
}

async function renderCampusCalendar(root) {
  $('#section-kicker').textContent = 'CAMPUS CALENDAR';
  $('#section-title').textContent = currentCampus.name + ' 학교 일정';
  $('#section-sub').textContent = '학교 공지와 같은 학교 학생들이 등록한 행사를 확인하세요.';
  $('#search').placeholder = '일정 이름이나 종류를 검색해 보세요';
  if (campusCalendar.school !== currentCampus.code) {
    Object.assign(campusCalendar, {
      month: campusCalendarToday.slice(0, 7),
      category: '전체',
      day: '',
      school: currentCampus.code,
    });
  }
  const calendar = el('div', 'campus-calendar');
  const actions = el('div', 'calendar-actions');
  const isStudent = currentSession.authenticated && currentSession.member.role === 'STUDENT';
  const guidance = isStudent
    ? '축제·학과 MT·동아리 행사를 같은 학교 학생들과 공유해 보세요.'
    : '학생 계정으로 로그인하면 학교 일정을 직접 등록할 수 있어요.';
  const register = calendarButton('＋ 일정 등록', '학교 행사 등록', async () => {
    if (!currentSession.authenticated) {
      location.href = '/login.html?next=calendar';
      return;
    }
    if (currentSession.member.role !== 'STUDENT') {
      toast('학생 계정으로 로그인하면 학교 행사를 등록할 수 있어요.');
      return;
    }
    if (register.disabled) return;
    register.disabled = true;
    try {
      const studentPage = await api('campus/events');
      if (location.hash === '#life/calendar') openStudentEventForm(studentPage.csrfToken);
    } catch (failure) {
      toast(failure.message);
    } finally {
      register.disabled = false;
    }
  });
  register.className = 'primary calendar-register';
  actions.append(el('p', '', guidance), register);
  calendar.append(el('p', 'calendar-message', '학교 일정을 불러오는 중이에요.'));
  root.append(actions, calendar);
  try {
    campusCalendarRequest ??= fetch('/preview/campus-events.json', { cache: 'no-cache' })
      .then((response) => {
        if (!response.ok) throw Error('schedule unavailable');
        return response.json();
      })
      .catch((error) => {
        campusCalendarRequest = null;
        throw error;
      });
    const [data, studentPage] = await Promise.all([
      campusCalendarRequest,
      isStudent ? api('campus/events') : null,
    ]);
    if (!calendar.isConnected) return;
    const studentEvents = (studentPage?.events || []).map((event) => ({ ...event, student: true }));
    const events = [...(data.campuses[currentCampus.code] || []), ...studentEvents].sort(
      (a, b) => a.start.localeCompare(b.start) || a.title.localeCompare(b.title, 'ko'),
    );
    if (data.asOf) calendar.dataset.asOf = data.asOf;
    drawCampusCalendar(calendar, events);
  } catch {
    if (!calendar.isConnected) return;
    calendar.replaceChildren(
      el('p', 'calendar-message', '학교 일정을 불러오지 못했어요.'),
      calendarButton('다시 시도', '학교 일정 다시 불러오기', renderShops),
    );
  }
}

function openStudentEventForm(csrfToken) {
  const dialog = modal('학교 행사 등록', currentCampus.name + ' 학생들의 달력에 표시돼요.');
  const form = el('form');
  addField(form, '행사명', 'title').maxLength = 100;
  const kind = addField(form, '종류', 'category', '', 'select');
  kind.append(new Option('행사 (학과 MT·동아리 등)', '행사'), new Option('축제', '축제'));
  const date = addField(form, '날짜', 'start');
  date.type = 'date';
  date.min = '1000-01-01';
  date.max = '9999-12-31';
  date.value =
    campusCalendar.day ||
    (campusCalendar.month === campusCalendarToday.slice(0, 7)
      ? campusCalendarToday
      : campusCalendar.month + '-01');
  addField(form, '장소', 'location').maxLength = 120;
  const audience = addField(form, '참여 대상', 'audience');
  audience.maxLength = 120;
  audience.placeholder = '예: 컴퓨터공학과 학생, 전체 학생';
  const error = el('p', 'rate-error');
  error.setAttribute('role', 'alert');
  const submit = el('button', 'primary', '행사 등록');
  submit.type = 'submit';
  form.append(error, submit);
  form.onsubmit = async (event) => {
    event.preventDefault();
    if (submit.disabled || !form.reportValidity()) return;
    submit.disabled = true;
    error.textContent = '';
    try {
      const result = await api('campus/events', 'POST', Object.fromEntries(new FormData(form)), {
        'X-CSRF-Token': csrfToken,
      });
      Object.assign(campusCalendar, {
        month: result.start.slice(0, 7),
        day: result.start,
        category: '전체',
      });
      $('#search').value = '';
      $('#modal').close();
      renderShops();
      toast('학교 달력에 행사를 등록했어요.');
    } catch (failure) {
      error.textContent = failure.message;
    } finally {
      submit.disabled = false;
    }
  };
  dialog.append(form);
  form.elements.title.focus();
}

function drawCampusCalendar(root, events, focusLabel) {
  const state = campusCalendar;
  const [year, month] = state.month.split('-').map(Number);
  const query = $('#search').value.trim().toLocaleLowerCase('ko');
  const matches = events.filter(
    (e) =>
      (state.category === '전체' || e.category === state.category) &&
      (e.title + ' ' + e.category).toLocaleLowerCase('ko').includes(query),
  );
  const visible = calendarMonthEvents(matches, state.month);
  const refresh = (label) => drawCampusCalendar(root, events, label);
  const goMonth = (value, label) => {
    state.month = value;
    state.day = '';
    refresh(label);
  };
  const shiftMonth = (step) =>
    new Date(Date.UTC(year, month - 1 + step, 1)).toISOString().slice(0, 7);
  const monthCard = el('section', 'calendar-sheet');
  const top = el('div', 'calendar-bar');
  const monthHeading = el('h3', '', year + '년 ' + month + '월');
  monthHeading.id = 'campus-calendar-month';
  monthCard.setAttribute('aria-labelledby', monthHeading.id);
  const controls = el('div', 'calendar-month-controls');
  controls.append(
    calendarButton('‹', '이전 달', () => goMonth(shiftMonth(-1), '이전 달')),
    calendarButton('오늘', '오늘이 있는 달', () =>
      goMonth(campusCalendarToday.slice(0, 7), '오늘이 있는 달'),
    ),
    calendarButton('›', '다음 달', () => goMonth(shiftMonth(1), '다음 달')),
  );
  top.append(monthHeading, controls);

  const table = el('table', 'calendar-grid');
  table.setAttribute('aria-labelledby', monthHeading.id);
  const head = table.createTHead().insertRow();
  for (const weekday of '일월화수목금토') {
    const th = el('th', '', weekday);
    th.scope = 'col';
    head.append(th);
  }
  const body = table.createTBody();
  const firstWeekday = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const cellCount = Math.ceil((firstWeekday + lastDay) / 7) * 7;
  for (let cell = 0, row; cell < cellCount; cell++) {
    if (cell % 7 === 0) row = body.insertRow();
    const td = row.insertCell();
    const day = cell - firstWeekday + 1;
    if (day < 1 || day > lastDay) continue;
    const iso = state.month + '-' + String(day).padStart(2, '0');
    const daily = visible.filter(
      (event) => event.start <= iso && (event.end || event.start) >= iso,
    );
    const dateLabel = month + '월 ' + day + '일 · 일정 ' + daily.length + '건';
    const display = daily.length
      ? calendarButton(String(day), dateLabel, () => {
          state.day = state.day === iso ? '' : iso;
          refresh(dateLabel);
        })
      : el('time', '', String(day));
    if (daily.length) {
      display.setAttribute('aria-pressed', String(state.day === iso));
      const dots = el('span', 'calendar-dots');
      dots.setAttribute('aria-hidden', 'true');
      for (const kind of new Set(daily.map((event) => event.category))) {
        const dot = el('i');
        dot.dataset.kind = kind;
        dots.append(dot);
      }
      display.append(dots);
    } else display.dateTime = iso;
    if (iso === campusCalendarToday) display.setAttribute('aria-current', 'date');
    td.append(display);
  }
  const filters = el('div', 'calendar-filters');
  filters.setAttribute('role', 'group');
  filters.setAttribute('aria-label', '일정 종류');
  for (const kind of ['전체', '축제', '학사', '행사']) {
    const button = calendarButton(kind, kind + ' 일정', () => {
      state.category = kind;
      state.day = '';
      const filtered = events.filter(
        (e) =>
          (kind === '전체' || e.category === kind) &&
          (e.title + ' ' + e.category).toLocaleLowerCase('ko').includes(query),
      );
      if (kind !== '전체' && !calendarMonthEvents(filtered, state.month).length) {
        state.month = nearestCalendarMonth(filtered, state.month + '-15') || state.month;
      }
      refresh(kind + ' 일정');
    });
    button.dataset.kind = kind;
    button.setAttribute('aria-pressed', String(kind === state.category));
    filters.append(button);
  }
  monthCard.append(top, table, filters);

  const agenda = el('section', 'calendar-agenda');
  const heading = el('div', 'calendar-agenda-heading');
  const listed = state.day
    ? visible.filter((e) => e.start <= state.day && (e.end || e.start) >= state.day)
    : visible;
  heading.append(
    el(
      'h3',
      '',
      (state.day ? calendarDateFormat.format(calendarDate(state.day)) : month + '월') + ' 일정',
    ),
    el('span', 'calendar-count', listed.length + '건'),
  );
  if (state.day)
    heading.append(
      calendarButton('한 달 전체', '한 달 전체 일정', () => goMonth(state.month, '전체 일정')),
    );
  agenda.append(heading);
  if (listed.length) {
    const list = el('ul', 'calendar-event-list');
    for (const event of listed) {
      const item = el('li', 'calendar-event');
      const meta = el('div', 'calendar-event-meta');
      const kind = el('span', 'calendar-kind', event.category);
      kind.dataset.kind = event.category;
      const status =
        (event.end || event.start) < campusCalendarToday
          ? '지난 일정'
          : event.start > campusCalendarToday
            ? '예정'
            : '진행 중';
      const origin = el(
        'span',
        'calendar-origin',
        event.student ? '학생 등록 행사' : event.sourceLabel || '공식 일정',
      );
      if (event.student) origin.classList.add('calendar-student-origin');
      meta.append(origin, kind, el('span', '', calendarEventRange(event)), el('small', '', status));
      item.append(meta);
      if (event.student) {
        item.append(el('strong', 'calendar-event-title', event.title));
        const details = el('dl', 'calendar-event-details');
        details.append(
          el('dt', '', '장소'),
          el('dd', '', event.location),
          el('dt', '', '참여 대상'),
          el('dd', '', event.audience),
        );
        item.append(details);
      } else {
        const source = el('a', 'calendar-source');
        source.href = event.url;
        source.target = '_blank';
        source.rel = 'noopener';
        const linkLabel = event.sourceLabel ? '출처 보기' : '공식 안내';
        source.append(el('strong', '', event.title), el('span', '', linkLabel + ' ↗'));
        source.setAttribute('aria-label', event.title + ' ' + linkLabel + ' (새 창)');
        item.append(source);
      }
      list.append(item);
    }
    agenda.append(list);
  } else {
    agenda.append(
      el(
        'p',
        'calendar-message',
        query
          ? '검색 조건에 맞는 이번 달 일정이 없어요.'
          : state.category === '전체'
            ? '이 달에 등록된 일정이 없어요.'
            : '이 달에 등록된 ' + state.category + ' 일정이 없어요.',
      ),
    );
    const nearest = nearestCalendarMonth(matches, state.month + '-15');
    if (nearest && nearest !== state.month) {
      const [nextYear, nextMonth] = nearest.split('-').map(Number);
      agenda.append(
        calendarButton(nextYear + '년 ' + nextMonth + '월 일정 보기 →', '가까운 일정 보기', () =>
          goMonth(nearest, '전체 일정'),
        ),
      );
    }
  }
  if (root.dataset.asOf) {
    const checked = el(
      'time',
      'calendar-as-of',
      '공지 확인일 ' + root.dataset.asOf.replaceAll('-', '. ') + '.',
    );
    checked.dateTime = root.dataset.asOf;
    agenda.append(checked);
  }
  root.replaceChildren(monthCard, agenda);
  if (focusLabel)
    [...root.querySelectorAll('button')]
      .find((button) => button.getAttribute('aria-label') === focusLabel)
      ?.focus({ preventScroll: true });
}
