// The form co-pilot: which profile value an application form's field asks for, and the filling itself, run in the page
// the freelancer chose (it never submits). Pure, so its matching is tested with Node.

/**
 * The profile values a form can be filled with, from Indezy's profile (GET /api/application-profile), empty ones left out.
 */
export function valuesOf(profile) {
  const values = {
    firstName: profile?.firstName, lastName: profile?.lastName,
    fullName: [profile?.firstName, profile?.lastName].filter(Boolean).join(' '),
    email: profile?.email, phone: profile?.phone, city: profile?.city, linkedin: profile?.linkedin, github: profile?.github,
    website: profile?.website, title: profile?.position, rate: profile?.targetDailyRate == null ? null : String(profile.targetDailyRate),
    availability: profile?.availabilityDate
  };
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== ''));
}

/**
 * Fills the page's empty fields from the values, by what each field says it is (its autocomplete, name, id, placeholder,
 * label), in French or English; never a password, a hidden or a disabled field, nor one already typed in; the CV (its
 * name, type and Base64 content), when given, in the empty file field asking for one. Runs in the page:
 * self-contained, it returns how many fields it filled.
 */
export function fillFields(values, cv) {
  const RULES = [
    ['email', /\b(e-?mail|courriel|mail)\b/],
    ['phone', /\b(phone|t[ée]l[ée]phone|tel|mobile|portable|gsm)\b/],
    ['linkedin', /linkedin/],
    ['github', /github/],
    ['website', /\b(website|site web|site internet|portfolio|url)\b/],
    ['firstName', /\b(first.?name|given.?name|pr[ée]nom|fname)\b/],
    ['lastName', /\b(last.?name|family.?name|surname|nom de famille|lname)\b|^nom\b|\bnom$/],
    ['fullName', /\b(full.?name|your name|nom complet|name)\b/],
    ['city', /\b(city|ville|localit[ée]|town)\b/],
    ['rate', /\b(tjm|daily.?rate|day.?rate|taux journalier|rate)\b/],
    ['availability', /\b(availab|disponib|start.?date|date de d[ée]but)/],
    ['title', /\b(job.?title|current.?title|position|poste|titre|headline)\b/]
  ];
  const AUTOCOMPLETE = { 'given-name': 'firstName', 'family-name': 'lastName', name: 'fullName', email: 'email', tel: 'phone',
    'address-level2': 'city', url: 'website', 'organization-title': 'title' };
  const describe = field => {
    const labels = [...(field.labels ?? [])].map(label => label.textContent).join(' ');
    return [field.name, field.id, field.placeholder, field.getAttribute('aria-label'), labels].filter(Boolean).join(' ')
      .toLowerCase().replace(/[_\-[\]]+/g, ' ');
  };
  let filled = 0;
  // The CV goes to the first empty file field that asks for one ("CV", "résumé", "curriculum"), a single file.
  if (cv?.base64) {
    const cvField = [...document.querySelectorAll('input[type=file]')].find(field => !field.disabled && !field.files?.length &&
      /\b(cv|r[ée]sum[ée]|resume|curriculum)\b/.test(describe(field)));
    if (cvField) {
      const bytes = Uint8Array.from(atob(cv.base64), character => character.charCodeAt(0));
      const transfer = new DataTransfer();
      transfer.items.add(new File([bytes], cv.name, { type: cv.type }));
      cvField.files = transfer.files;
      cvField.dispatchEvent(new Event('input', { bubbles: true }));
      cvField.dispatchEvent(new Event('change', { bubbles: true }));
      filled++;
    }
  }
  for (const field of document.querySelectorAll('input, textarea')) {
    const type = (field.getAttribute('type') || 'text').toLowerCase();
    if (field.disabled || field.readOnly || field.value || ['password', 'file', 'hidden', 'checkbox', 'radio', 'submit', 'button'].includes(type) ||
        field.offsetParent === null) {
      continue;
    }
    const auto = AUTOCOMPLETE[(field.getAttribute('autocomplete') || '').toLowerCase()];
    const text = describe(field);
    const kind = auto ?? (type === 'email' ? 'email' : type === 'tel' ? 'phone' : RULES.find(([, pattern]) => pattern.test(text))?.[0]);
    let value = kind ? values[kind] : undefined;
    if (value === undefined || (field.tagName === 'TEXTAREA' && kind !== 'fullName')) {
      continue;
    }
    if (type === 'date' && kind !== 'availability') {
      continue;
    }
    if (type === 'number' && !/^\d+([.,]\d+)?$/.test(String(value))) {
      continue;
    }
    value = String(value);
    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(field), 'value')?.set;
    if (setter) {
      setter.call(field, value);
    } else {
      field.value = value;
    }
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('change', { bubbles: true }));
    filled++;
  }
  return filled;
}
