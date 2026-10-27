import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { Offer, OfferLink } from '../../../../models/offer.models';
import { ProjectDto } from '../../../../models/project.models';
import { OfferService } from '../../../../services/offer/offer.service';
import { LOOK_AGAIN_MS, OfferLinkFieldComponent, READING_FOR_MS, messageOf } from './offer-link-field.component';

describe('OfferLinkFieldComponent', () => {
  const offer = { id: 7, title: 'Développeur Java', siteName: 'Free-Work', expired: false } as Offer;
  const found: OfferLink = {
    status: 'FOUND', siteName: 'Free-Work', offer,
    draft: { project: { role: 'Développeur Java', dailyRate: 650 }, clientName: 'Exemple Conseil', sourceName: 'Free-Work' }
  };
  let offers: jasmine.SpyObj<OfferService>;
  let fixture: ComponentFixture<OfferLinkFieldComponent>;
  let emitted: OfferLink[];

  beforeEach(() => {
    offers = jasmine.createSpyObj('OfferService', ['resolveLink']);
    TestBed.configureTestingModule({
      imports: [OfferLinkFieldComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: OfferService, useValue: offers }]
    });
    fixture = TestBed.createComponent(OfferLinkFieldComponent);
    emitted = [];
    fixture.componentInstance.found.subscribe(link => emitted.push(link));
    fixture.detectChanges();
  });

  function paste(url: string): void {
    fixture.componentInstance.link.setValue(url);
    fixture.componentInstance.fill();
    fixture.detectChanges();
  }

  function status(): string {
    return (fixture.nativeElement.querySelector('[data-link-status]') as HTMLElement).textContent?.trim() ?? '';
  }

  it('hands an offer found to the form and says so', () => {
    offers.resolveLink.and.returnValue(of(found));

    paste('  https://www.free-work.com/fr/mission/7  ');

    expect(offers.resolveLink).toHaveBeenCalledWith('https://www.free-work.com/fr/mission/7');
    expect(emitted).toEqual([found]);
    expect(status()).toContain('projects.offerLink.filled');
    expect(fixture.componentInstance.busy).toBeFalse();
  });

  it('warns that an offer found has expired', () => {
    offers.resolveLink.and.returnValue(of({ ...found, offer: { ...offer, expired: true } }));

    paste('https://www.free-work.com/fr/mission/7');

    expect(status()).toContain('projects.offerLink.expired');
  });

  it('opens the opportunity of a mission already in the pipeline', () => {
    offers.resolveLink.and.returnValue(of({ status: 'IN_PIPELINE', siteName: 'Free-Work', offer, projectId: 40 } as OfferLink));

    paste('https://www.free-work.com/fr/mission/7');

    const open = fixture.nativeElement.querySelector('[data-open-project]') as HTMLAnchorElement;
    expect(open.getAttribute('href')).toBe('/projects/40');
    expect(emitted).toEqual([]);
  });

  it('looks again while the site is being read', fakeAsync(() => {
    offers.resolveLink.and.returnValues(of({ status: 'READING', siteName: 'Free-Work' } as OfferLink), of(found));

    paste('https://www.free-work.com/fr/mission/7');
    expect(status()).toContain('projects.offerLink.reading');
    expect(fixture.componentInstance.busy).toBeTrue();

    tick(LOOK_AGAIN_MS);
    fixture.detectChanges();

    expect(offers.resolveLink).toHaveBeenCalledTimes(2);
    expect(emitted).toEqual([found]);
    expect(fixture.componentInstance.busy).toBeFalse();
  }));

  it('stops looking after two minutes of reading', fakeAsync(() => {
    offers.resolveLink.and.returnValue(of({ status: 'READING', siteName: 'Free-Work' } as OfferLink));

    paste('https://www.free-work.com/fr/mission/7');
    tick(READING_FOR_MS + LOOK_AGAIN_MS);
    fixture.detectChanges();

    expect(status()).toContain('projects.offerLink.stillReading');
    expect(fixture.componentInstance.busy).toBeFalse();
    const calls = offers.resolveLink.calls.count();
    tick(LOOK_AGAIN_MS * 3);
    expect(offers.resolveLink.calls.count()).toBe(calls);
  }));

  it('tells why a link gave no offer', () => {
    expect(messageOf({ status: 'SITE_UNAVAILABLE', siteName: 'acme-careers.fr' }))
      .toEqual({ key: 'projects.offerLink.status.SITE_UNAVAILABLE', tone: 'problem', site: 'acme-careers.fr' });
    expect(messageOf({ status: 'NOT_COLLECTED', siteName: 'Free-Work', retryAt: '2026-10-20T10:15:00Z' }))
      .toEqual({ key: 'projects.offerLink.status.NOT_COLLECTED_UNTIL', tone: 'problem', site: 'Free-Work', at: '2026-10-20T10:15:00Z' });
    expect(messageOf({ status: 'NOT_COLLECTED', siteName: 'Free-Work' }).key).toBe('projects.offerLink.status.NOT_COLLECTED');
    expect(messageOf({ status: 'NOT_FREELANCE', siteName: 'Mindquest' }))
      .toEqual({ key: 'projects.offerLink.status.NOT_FREELANCE', tone: 'problem', site: 'Mindquest' });
  });

  it('fills the form in from a posting read on a site the feed does not collect', () => {
    const posting: OfferLink = { status: 'POSTING', siteName: 'acme-careers.fr', draft: { project: { role: 'Développeur Java' } as ProjectDto,
      clientName: 'Acme', sourceName: 'acme-careers.fr', sourceLink: 'https://www.acme-careers.fr' } };

    expect(messageOf(posting))
      .toEqual({ key: 'projects.offerLink.filledFromPosting', tone: 'success', site: 'acme-careers.fr', title: 'Développeur Java' });
    offers.resolveLink.and.returnValue(of(posting));

    paste('https://www.acme-careers.fr/postes/developpeur-java');

    expect(emitted).toEqual([posting]);
    expect(status()).toContain('projects.offerLink.filledFromPosting');
  });

  it('tells a link refused by the server', () => {
    for (const [code, key] of [[400, 'invalid'], [429, 'tooMany'], [500, 'error']] as const) {
      offers.resolveLink.and.returnValue(throwError(() => new HttpErrorResponse({ status: code })));
      paste('https://www.free-work.com/fr/mission/7');
      expect(fixture.componentInstance.message?.key).toBe(`projects.offerLink.${key}`);
      expect(fixture.componentInstance.busy).toBeFalse();
    }
  });

  it('asks for a link before looking one up, and looks it up on Enter', () => {
    paste('   ');
    expect(offers.resolveLink).not.toHaveBeenCalled();
    expect(fixture.componentInstance.link.touched).toBeTrue();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('projects.offerLink.required');

    offers.resolveLink.and.returnValue(of(found));
    const input = fixture.nativeElement.querySelector('[data-link-input]') as HTMLInputElement;
    input.value = 'https://www.free-work.com/fr/mission/7';
    input.dispatchEvent(new Event('input'));
    const enter = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
    input.dispatchEvent(enter);

    expect(enter.defaultPrevented).toBeTrue();
    expect(offers.resolveLink).toHaveBeenCalledOnceWith('https://www.free-work.com/fr/mission/7');
  });

  it('looks one link up at a time', fakeAsync(() => {
    offers.resolveLink.and.returnValue(of({ status: 'READING', siteName: 'Free-Work' } as OfferLink));
    paste('https://www.free-work.com/fr/mission/7');

    fixture.componentInstance.fill();

    expect(offers.resolveLink).toHaveBeenCalledTimes(1);
    tick(READING_FOR_MS + LOOK_AGAIN_MS);
  }));

  it('looks up a link handed with the page at once, without taking the cursor', () => {
    offers.resolveLink.and.returnValue(of(found));
    const handed = TestBed.createComponent(OfferLinkFieldComponent);
    handed.componentInstance.initialLink = ' https://www.free-work.com/fr/mission/7 ';
    handed.componentInstance.autofocus = true;
    const received: OfferLink[] = [];
    handed.componentInstance.found.subscribe(link => received.push(link));
    handed.detectChanges();

    expect(handed.componentInstance.link.value).toBe('https://www.free-work.com/fr/mission/7');
    expect(offers.resolveLink).toHaveBeenCalledWith('https://www.free-work.com/fr/mission/7');
    expect(received).toEqual([found]);
    expect(document.activeElement).not.toBe(handed.nativeElement.querySelector('input'));
  });

  it('puts the cursor in the field when asked', () => {
    const focused = TestBed.createComponent(OfferLinkFieldComponent);
    focused.componentInstance.autofocus = true;
    document.body.appendChild(focused.nativeElement);
    focused.detectChanges();

    expect(document.activeElement).toBe(focused.nativeElement.querySelector('[data-link-input]'));
    focused.nativeElement.remove();
  });
});
