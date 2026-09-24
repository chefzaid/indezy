import { EnvironmentProviders, Injectable, LOCALE_ID, Provider, makeEnvironmentProviders } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import {
  DateAdapter,
  MAT_DATE_FORMATS,
  MAT_DATE_LOCALE,
  MAT_NATIVE_DATE_FORMATS,
  NativeDateAdapter
} from '@angular/material/core';

/** Storage key holding the language chosen in the toolbar. */
export const LANGUAGE_STORAGE_KEY = 'indezy-lang';

export type AppLanguage = 'fr' | 'en';

const LOCALE_BY_LANGUAGE: Record<AppLanguage, string> = {
  fr: 'fr-FR',
  en: 'en-US'
};

/** The saved UI language; French is the default for the French freelance market. */
export function getSavedLanguage(): AppLanguage {
  try {
    return localStorage.getItem(LANGUAGE_STORAGE_KEY) === 'en' ? 'en' : 'fr';
  } catch {
    return 'fr';
  }
}

/** Locale used for number, currency and date formatting in the saved UI language. */
export function getAppLocale(language: AppLanguage = getSavedLanguage()): string {
  return LOCALE_BY_LANGUAGE[language];
}

/**
 * Native date adapter that also understands day-first input (dd/MM/yyyy, dd.MM.yyyy,
 * dd-MM-yyyy) for non-US locales. The stock adapter relies on Date.parse, which reads
 * "12/08/2026" as December 8th even when the French locale is active.
 */
@Injectable()
export class AppDateAdapter extends NativeDateAdapter {
  private static readonly DAY_FIRST_PATTERN = /^\s*(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\s*$/;

  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string' && !this.locale?.toString().startsWith('en-US')) {
      const match = AppDateAdapter.DAY_FIRST_PATTERN.exec(value);
      if (match) {
        const [, day, month, year] = match.map(Number);
        const date = new Date(year, month - 1, day);
        const isRealDate = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
        return isRealDate ? date : this.invalid();
      }
    }
    return super.parse(value, parseFormat);
  }
}

/** Formats a date as an ISO calendar day (yyyy-MM-dd) using local time, as the API expects. */
export function toIsoDate(date: Date | string | null | undefined): string | undefined {
  if (!date) {
    return undefined;
  }
  if (typeof date === 'string') {
    return date.length >= 10 ? date.substring(0, 10) : date;
  }
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Parses an ISO calendar day (yyyy-MM-dd) as a local date, avoiding UTC day shifts. */
export function fromIsoDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
}

/** Registers locale data and wires Angular pipes and Material datepickers to the saved language. */
export function provideAppLocale(): EnvironmentProviders {
  registerLocaleData(localeFr);
  const providers: Provider[] = [
    { provide: LOCALE_ID, useFactory: () => getAppLocale() },
    { provide: MAT_DATE_LOCALE, useFactory: () => getAppLocale() },
    { provide: DateAdapter, useClass: AppDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: MAT_NATIVE_DATE_FORMATS }
  ];
  return makeEnvironmentProviders(providers);
}
