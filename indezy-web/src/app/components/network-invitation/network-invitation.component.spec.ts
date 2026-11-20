import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { NetworkInvitationsService } from '../../services/network/network-invitations.service';
import en from '../../../assets/i18n/en.json';
import { NetworkInvitationComponent } from './network-invitation.component';

describe('NetworkInvitationComponent', () => {
  let invitations: jasmine.SpyObj<NetworkInvitationsService>;

  function create(fragment: string | null, read = of({ inviter: 'Alice Martin', declined: false })): HTMLElement {
    invitations = jasmine.createSpyObj('NetworkInvitationsService', ['read', 'decline']);
    invitations.read.and.returnValue(read);
    TestBed.configureTestingModule({ imports: [NetworkInvitationComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { fragment } } },
        { provide: NetworkInvitationsService, useValue: invitations }] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkInvitationComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('says who invites, leads to the sign-up, and declines every invitation from a button', () => {
    const element = create('tok');
    expect(invitations.read).toHaveBeenCalledWith('tok');
    expect(element.querySelector('h1')?.textContent).toContain('Alice Martin invites you to the Indezy network');
    expect(element.querySelector('[data-invitation-register]')?.getAttribute('href')).toBe('/register');
    invitations.decline.and.returnValue(of({ inviter: 'Alice Martin', declined: true }));
    (element.querySelector('[data-invitation-decline]') as HTMLButtonElement).click();
    TestBed.inject(TranslateService);
    expect(invitations.decline).toHaveBeenCalledWith('tok');
  });

  it('says when the link is invalid', () => {
    expect(create(null).querySelector('[data-invalid-link]')).not.toBeNull();
    TestBed.resetTestingModule();
    expect(create('gone', throwError(() => new Error('404'))).querySelector('[data-invalid-link]')).not.toBeNull();
  });
});
