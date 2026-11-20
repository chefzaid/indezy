import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { MemberCard, NetworkAbout, NetworkMembership } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { CardStrengthComponent } from './card-strength.component';

describe('CardStrengthComponent', () => {
  const fields = { photo: true, headline: true, stack: true, location: false, availability: true, languages: true, remote: false };
  const card = (changes: Partial<MemberCard> = {}): MemberCard => ({ handle: 'camille', firstName: 'Camille', lastName: 'Durand',
    avatar: null, headline: 'Architecte Java', stack: ['Java', 'Kafka'], city: null, remote: false, availability: 'AVAILABLE',
    availabilityDate: null, languages: [], verified: false, ...changes });
  const membership = (changes: Partial<NetworkMembership> = {}): NetworkMembership => ({ joined: true, handle: 'camille', fields,
    availability: { mode: 'AUTO', date: null }, card: card(), privateCard: false, esnSuggestions: false, ...changes });

  function create(member: Observable<NetworkMembership>, about: Observable<NetworkAbout> = of({ about: null, showMissions: false })) {
    const network = jasmine.createSpyObj<NetworkService>('NetworkService', ['membership', 'myAbout']);
    network.membership.and.returnValue(member);
    network.myAbout.and.returnValue(about);
    TestBed.configureTestingModule({ imports: [CardStrengthComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: NetworkService, useValue: network }] });
    const fixture = TestBed.createComponent(CardStrengthComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('measures what the card shows and links each missing item to where it is set', () => {
    const fixture = create(of(membership()));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    // A headline and an availability of six items.
    expect(component.percent).toBe(33);
    expect(component.level).toBe('low');
    expect(component.gaps.map(gap => gap.item)).toEqual(['photo', 'skills', 'city', 'about']);
    expect(element.querySelector('[data-strength-gap="photo"] a')?.getAttribute('href')).toBe('/profile');
    // The city is hidden from the card: shown from Profile → Network.
    expect(component.gaps.find(gap => gap.item === 'city')).toEqual({ item: 'city', hidden: true, tab: 'network' });
    expect(element.querySelector('[data-strength-gap="city"]')?.textContent).toContain('network.strength.show.city');
    expect(element.querySelector('[data-strength-gap="about"] a')?.getAttribute('href')).toBe('/profile?tab=network');
  });

  it('says a full card is complete, and shows nothing outside the network', () => {
    let fixture = create(of(membership({ card: card({ avatar: '/a.png', stack: ['Java', 'Kafka', 'Spark'], city: 'Lyon' }) })),
      of({ about: 'Architecte', showMissions: true }));
    expect(fixture.componentInstance.percent).toBe(100);
    expect(fixture.componentInstance.level).toBe('complete');
    expect(fixture.nativeElement.querySelector('[data-strength-gap]')).toBeNull();
    TestBed.resetTestingModule();
    fixture = create(of(membership({ card: card({ avatar: '/a.png', stack: ['Java', 'Kafka', 'Spark'] }) })), throwError(() => new Error('x')));
    expect(fixture.componentInstance.level).toBe('good');
    TestBed.resetTestingModule();
    fixture = create(of(membership({ joined: false, card: null })));
    expect(fixture.nativeElement.querySelector('[data-card-strength]')).toBeNull();
    TestBed.resetTestingModule();
    fixture = create(throwError(() => new Error('down')));
    expect(fixture.componentInstance.percent).toBeNull();
  });
});
