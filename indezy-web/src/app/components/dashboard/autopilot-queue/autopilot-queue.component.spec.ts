import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { AiAutopilotQueue } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { AutopilotQueueComponent, PREPARED_SHOWN } from './autopilot-queue.component';

describe('AutopilotQueueComponent', () => {
  const offer = (id: number, goodFit: boolean) => ({ offerId: id, title: 'Lead Java ' + id, company: id % 2 ? 'Conseil' : null, goodFit,
    verdict: goodFit ? 'Bonne mission.' : 'Sur site tous les jours.', dealBreakers: goodFit ? [] : ['5 jours sur site'], at: '2026-11-24T04:10:00' });

  function create(queue: AiAutopilotQueue): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AutopilotQueueComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: AiService, useValue: { autopilot: () => of(queue) } }]
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

  it('stays away while off or when the night found nothing', () => {
    expect(create({ on: false, offers: [offer(1, true)] }).querySelector('[data-autopilot-queue]')).toBeNull();
    expect(create({ on: true, offers: [] }).querySelector('[data-autopilot-queue]')).toBeNull();
  });
});
