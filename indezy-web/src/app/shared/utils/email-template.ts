/** The variables a message template can name, written {{firstName}} and so on. */
export const EMAIL_VARIABLES = ['firstName', 'lastName', 'company', 'role', 'rate', 'availability', 'myName', 'myPhone',
  'privacyNotice'] as const;
export type EmailVariable = typeof EMAIL_VARIABLES[number];
export type EmailValues = Partial<Record<EmailVariable, string | null | undefined>>;

/** Replaces each {{variable}} with its value; one without a value stays as written, for the sender to complete. */
export function fillTemplate(text: string, values: EmailValues): string {
  return text.replace(/\{\{\s*(\w+)\s*\}\}/g, (written, name: string) =>
    (EMAIL_VARIABLES as readonly string[]).includes(name) ? values[name as EmailVariable]?.trim() || written : written);
}

/** A mailto: address opening the mail app with the recipient (when it is an address), subject and body. */
export function mailtoLink(to: string | null | undefined, subject: string, body: string): string {
  const recipient = to && /^[^\s@?&#]+@[^\s@?&#]+$/.test(to) ? to : '';
  return `mailto:${recipient}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}
