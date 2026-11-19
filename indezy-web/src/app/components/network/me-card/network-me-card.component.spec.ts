import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberPage, NetworkMembership } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NetworkMeCardComponent } from './network-me-card.component';

describe('NetworkMeCardComponent', () => {
  const page: MemberPage = { card: { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null, headline: 'Architecte Java',
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false }, followers: 12,
    following: 4, sharedSkills: [], followed: false, followsYou: false, own: true, muted: false, blocked: false, privateCard: false,
    requested: false };

  function create(membership = of({ joined: true, handle: 'camille' } as NetworkMembership), read = of(page)) {
    const network = jasmine.createSpyObj<NetworkService>('NetworkService', ['membership', 'page']);
    network.membership.and.returnValue(membership);
    network.page.and.returnValue(read);
    TestBed.configureTestingModule({
      imports: [NetworkMeCardComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: NetworkService, useValue: network }]
    });
    const fixture = TestBed.createComponent(NetworkMeCardComponent);
    fixture.detectChanges();
    return { fixture, network, element: fixture.nativeElement as HTMLElement };
  }

  it('shows the reader\'s page with its counts and its tabs, each one click away', () => {
    const { fixture, network, element } = create();
    expect(network.membership).toHaveBeenCalledWith(true);
    expect(network.page).toHaveBeenCalledWith('camille');
    expect(element.querySelector('[data-me-page]')?.getAttribute('href')).toBe('/m/camille');
    expect(element.querySelector('[data-me-page]')?.textContent).toContain('Camille Durand');
    expect(element.textContent).toContain('Architecte Java');
    expect(element.querySelector('[data-me-followers]')?.textContent).toContain('12');
    expect(element.querySelector('[data-me-followers]')?.getAttribute('href')).toBe('/m/camille?tab=followers');
    expect(element.querySelector('[data-me-following]')?.getAttribute('href')).toBe('/m/camille?tab=following');
    expect(element.querySelector('[data-me-saved]')?.getAttribute('href')).toBe('/m/camille?tab=saved');
    expect(element.querySelector('[data-me-drafts]')?.getAttribute('href')).toBe('/m/camille?tab=drafts');
    expect(fixture.componentInstance.cover).toContain('linear-gradient');
  });

  it('shows nothing to a visitor not in the network, or when the page cannot be read', () => {
    let { element } = create(of({ joined: false, handle: 'suggested' } as NetworkMembership), of({ ...page, own: false }));
    expect(element.querySelector('[data-me-card]')).toBeNull();
    TestBed.resetTestingModule();
    ({ element } = create(throwError(() => new Error('down'))));
    expect(element.querySelector('[data-me-card]')).toBeNull();
  });
});
