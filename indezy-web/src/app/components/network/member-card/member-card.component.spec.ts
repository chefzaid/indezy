import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { MemberCard } from '../../../models';
import { MemberCardComponent } from './member-card.component';
import en from '../../../../assets/i18n/en.json';

describe('MemberCardComponent', () => {
  const base: MemberCard = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: 'https://example.com/leo.png',
    headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [],
    verified: false };

  function render(card: Partial<MemberCard>): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [MemberCardComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(MemberCardComponent);
    fixture.componentRef.setInput('card', { ...base, ...card });
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('tells each availability in words and by the ring around the photo', () => {
    const cases: [Partial<MemberCard>, string, string][] = [
      [{ availability: 'AVAILABLE' }, 'Available now', 'AVAILABLE'],
      [{ availability: 'AVAILABLE', availabilityDate: '2026-12-02' }, 'Available from December 2, 2026', 'AVAILABLE'],
      [{ availability: 'ON_MISSION' }, 'On a mission', 'ON_MISSION'],
      [{ availability: 'ON_MISSION', availabilityDate: '2027-03-31' }, 'On a mission until March 2027', 'ON_MISSION'],
      [{ availability: 'LISTENING' }, 'Open to offers', 'LISTENING']
    ];
    for (const [card, text, ring] of cases) {
      const element = render(card);
      expect(element.querySelector('[data-card-availability]')?.textContent?.replace(/\s+/g, ' ')).toContain(text);
      expect(element.querySelector('.ring')?.classList).toContain(ring);
    }
    const hidden = render({});
    expect(hidden.querySelector('[data-card-availability]')).toBeNull();
    expect(hidden.querySelector('.ring')?.classList).toContain('NONE');
    expect(hidden.querySelector('img.photo')?.getAttribute('src')).toBe('https://example.com/leo.png');
    expect(hidden.querySelector('[data-card-verified]')).toBeNull();
    expect(render({ verified: true }).querySelector('[data-card-verified]')?.getAttribute('aria-label')).toContain('Verified freelancer');
  });
});
