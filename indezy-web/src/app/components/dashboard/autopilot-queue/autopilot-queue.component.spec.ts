import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { Observable, of } from 'rxjs';
import { NotificationService } from '../../../services/notification/notification.service';

import { AiAutopilotQueue } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { AutopilotQueueComponent, PREPARED_SHOWN } from './autopilot-queue.component';

describe('AutopilotQueueComponent', () => {
  const offer = (id: number, goodFit: boolean) => ({ offerId: id, title: 'Lead Java ' + id, company: id % 2 ? 'Conseil' : null, goodFit,
    verdict: goodFit ? 'Bonne mission.' : 'Sur site tous les jours.', dealBreakers: goodFit ? [] : ['5 jours sur site'], at: '2026-11-24T04:10:00' });

  let ai: { autopilot: () => Observable<AiAutopilotQueue>; dismissDraft: jasmine.Spy; dismissPack: jasmine.Spy };
  let clipboard: jasmine.SpyObj<Clipboard>;

  function create(queue: AiAutopilotQueue): HTMLElement {
    TestBed.resetTestingModule();
    ai = { autopilot: () => of(queue), dismissDraft: jasmine.createSpy('dismissDraft').and.returnValue(of(undefined)),
      dismissPack: jasmine.createSpy('dismissPack').and.returnValue(of(undefined)) };
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    TestBed.configureTestingModule({
      imports: [AutopilotQueueComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: AiService, useValue: ai }, { provide: Clipboard, useValue: clipboard },
        { provide: NotificationService, useValue: jasmine.createSpyObj('NotificationService', ['success', 'error']) }]
    });
    const fixture = TestBed.createComponent(AutopilotQueueComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists what the night triaged, worth applying to first, five at most', () => {
    const element = create({ on: true, offers: [offer(1, true), offer(2, true), offer(3, false), offer(4, false), offer(5, false), offer(6, false)] });
    expect(element.querySelectorAll('[data-autopilot-offer]').length).toBe(PREPARED_SHOWN);
    expect(element.querySelector('[data-autopilot-offer="1"] a')!.getAttribute('href')).toBe('/offers/1');
    expect(element.querySelector('[data-autopilot-offer="3"]')!.textContent).toContain('5 jours sur site');
  });

  it('lists the follow-ups drafted, to copy or set aside', () => {
    TestBed.resetTestingModule();
    const element = create({ on: true, offers: [], drafts: [{ id: 5, contactId: 2, contact: 'Léa Martin', company: 'Conseil', projectId: 41,
      subject: 'Point sur la mission', body: 'Bonjour Léa,', at: '2026-11-24T04:10:00' }] });
    const draft = element.querySelector('[data-autopilot-draft="5"]')!;
    expect(draft.textContent).toContain('Point sur la mission');
    (draft.querySelector('[data-autopilot-copy]') as HTMLButtonElement).click();
    expect(clipboard.copy).toHaveBeenCalledWith('Point sur la mission\n\nBonjour Léa,');
    (draft.querySelector('[data-autopilot-dismiss]') as HTMLButtonElement).click();
    expect(ai.dismissDraft).toHaveBeenCalledWith(5);
  });

  it('lists the applications prepared, with their rate, to copy or set aside', () => {
    const element = create({ on: true, offers: [], packs: [{ id: 8, offerId: 12, title: 'Lead Kotlin', company: 'Conseil', subject: 'Candidature',
      message: 'Bonjour,', screening: ['Disponibilité — Janvier'], rate: 650, at: '2026-11-24T04:10:00' }] });
    const pack = element.querySelector('[data-autopilot-pack="8"]')!;
    expect(pack.querySelector('[data-autopilot-pack-rate]')!.textContent).toContain('dashboard.autopilot.rate');
    expect(pack.textContent).toContain('Disponibilité — Janvier');
    (pack.querySelector('[data-autopilot-pack-copy]') as HTMLButtonElement).click();
    expect(clipboard.copy).toHaveBeenCalledWith('Candidature\n\nBonjour,');
    (pack.querySelector('[data-autopilot-pack-dismiss]') as HTMLButtonElement).click();
    expect(ai.dismissPack).toHaveBeenCalledWith(8);
  });

  it('stays away while off or when the night found nothing', () => {
    expect(create({ on: false, offers: [offer(1, true)] }).querySelector('[data-autopilot-queue]')).toBeNull();
    expect(create({ on: true, offers: [] }).querySelector('[data-autopilot-queue]')).toBeNull();
  });

  it('shows the answers the inbox watcher drafted while the autopilot is off, not its offers', () => {
    const element = create({ on: false, offers: [offer(1, true)], drafts: [{ id: 5, contactId: 3, contact: 'Inès Roy', company: 'Acme',
      projectId: null, subject: 'Re: Dispo ?', body: 'Bonjour,', at: '2026-11-24T10:00:00', answer: true }] });
    expect(element.querySelector('[data-autopilot-queue]')).not.toBeNull();
    expect(element.textContent).toContain('Re: Dispo ?');
    expect(element.querySelector('[data-autopilot-offer]')).toBeNull();
    // Named an answer, the summary counting only what was drafted, no offers triaged with the autopilot off.
    expect(element.textContent).toContain('dashboard.autopilot.answerFor');
    expect(element.querySelector('[data-autopilot-summary]')!.textContent).toContain('dashboard.autopilot.summaryDrafts');
  });
});
