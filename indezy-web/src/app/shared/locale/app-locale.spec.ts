import { TestBed } from '@angular/core/testing';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { AppDateAdapter, fromIsoDate, toIsoDate } from './app-locale';

describe('app locale helpers', () => {
  it('serializes local calendar days without a UTC shift', () => {
    expect(toIsoDate(new Date(2026, 9, 15, 0, 30))).toBe('2026-10-15');
    expect(toIsoDate('2026-10-15T00:00:00')).toBe('2026-10-15');
    expect(toIsoDate(null)).toBeUndefined();
  });

  it('parses ISO calendar days as local dates', () => {
    const date = fromIsoDate('2026-10-15')!;
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 9, 15]);
    expect(fromIsoDate(undefined)).toBeNull();
  });
});

describe('AppDateAdapter', () => {
  function adapterFor(locale: string): AppDateAdapter {
    TestBed.configureTestingModule({
      providers: [AppDateAdapter, { provide: MAT_DATE_LOCALE, useValue: locale }]
    });
    return TestBed.inject(AppDateAdapter);
  }

  it('reads day-first input in French', () => {
    const date = adapterFor('fr-FR').parse('12/08/2026', null)!;
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 7, 12]);
  });

  it('rejects impossible day-first dates', () => {
    const adapter = adapterFor('fr-FR');
    expect(adapter.isValid(adapter.parse('31/02/2026', null)!)).toBeFalse();
  });

  it('keeps month-first parsing for US English', () => {
    const date = adapterFor('en-US').parse('12/08/2026', null)!;
    expect([date.getMonth(), date.getDate()]).toEqual([11, 8]);
  });
});
