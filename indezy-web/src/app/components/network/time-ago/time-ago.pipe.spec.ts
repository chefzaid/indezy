import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { TimeAgoPipe } from './time-ago.pipe';

describe('TimeAgoPipe', () => {
  function pipe(language: string): TimeAgoPipe {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation(language, { network: { time: { now: language === 'en' ? 'now' : 'à l’instant' } } });
    translate.use(language);
    return TestBed.runInInjectionContext(() => new TimeAgoPipe());
  }

  it('tells minutes, hours and days, then the date', () => {
    const now = new Date('2026-11-15T12:00:00');
    const ago = pipe('en');
    expect(ago.transform('2026-11-15T11:59:30', now)).toBe('now');
    expect(ago.transform('2026-11-15T11:55:00', now)).toBe('5 min. ago');
    expect(ago.transform('2026-11-15T09:00:00', now)).toBe('3 hr. ago');
    expect(ago.transform(new Date('2026-11-13T12:00:00'), now)).toBe('2 days ago');
    expect(ago.transform('2026-11-02T12:00:00', now)).toBe('Nov 2');
    expect(ago.transform('2025-11-02T12:00:00', now)).toBe('Nov 2, 2025');
    expect(ago.transform(null, now)).toBe('');
  });

  it('speaks French in French', () => {
    // French puts a narrow no-break space before the unit.
    expect(pipe('fr').transform('2026-11-15T09:00:00', new Date('2026-11-15T12:00:00')).replace(/\s/g, ' ')).toBe('il y a 3 h');
  });
});
