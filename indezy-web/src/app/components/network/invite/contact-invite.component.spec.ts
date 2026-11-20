import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ContactStanding } from '../../../models';
import { CapabilitiesService } from '../../../services/admin/capabilities.service';
import { NetworkInvitationsService } from '../../../services/network/network-invitations.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import en from '../../../../assets/i18n/en.json';
import { ContactInviteComponent } from './contact-invite.component';

describe('ContactInviteComponent', () => {
  let invitations: jasmine.SpyObj<NetworkInvitationsService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const standing = (kind: ContactStanding['standing'], changes: Partial<ContactStanding> = {}): ContactStanding =>
    ({ standing: kind, invitedAt: null, handle: null, ...changes });

  function create(found = of(standing('INVITABLE')), network = true) {
    invitations = jasmine.createSpyObj('NetworkInvitationsService', ['standing', 'invite']);
    invitations.standing.and.returnValue(found);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({ imports: [ContactInviteComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkInvitationsService, useValue: invitations }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: CapabilitiesService, useValue: { snapshot: () => ({ admin: false, features: { SOCIAL_NETWORK: network } }) } }] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ContactInviteComponent);
    fixture.componentRef.setInput('contactId', 7);
    fixture.componentRef.setInput('name', 'Carol Test');
    fixture.detectChanges();
    return fixture;
  }

  it('invites an invitable contact once confirmed, then says when', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(invitations.standing).toHaveBeenCalledWith(7);
    confirm.confirm.and.returnValue(of(false));
    (element.querySelector('[data-contact-invite-button]') as HTMLButtonElement).click();
    expect(invitations.invite).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    invitations.invite.and.returnValue(of(standing('INVITED', { invitedAt: '2026-11-20T10:00:00' })));
    fixture.componentInstance.invite();
    fixture.detectChanges();
    expect(invitations.invite).toHaveBeenCalledWith(7);
    expect(notifications.success).toHaveBeenCalledWith('network.invite.sent', undefined, { name: 'Carol Test' });
    expect(element.querySelector('[data-contact-invited]')?.textContent).toContain('Invited to the network on');
    invitations.invite.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    fixture.componentInstance.invite();
    expect(notifications.error).toHaveBeenCalledWith('network.invite.tooMany');
  });

  it('links a member\'s page, says who declined, and shows nothing without an address or the network', () => {
    let element: HTMLElement = create(of(standing('MEMBER', { handle: 'carol_t' }))).nativeElement;
    expect(element.querySelector('[data-contact-member]')?.getAttribute('href')).toBe('/m/carol_t');
    TestBed.resetTestingModule();
    element = create(of(standing('DECLINED'))).nativeElement;
    expect(element.querySelector('[data-contact-declined]')).not.toBeNull();
    TestBed.resetTestingModule();
    element = create(of(standing('NO_EMAIL'))).nativeElement;
    expect(element.querySelector('[data-contact-invite]')).toBeNull();
    TestBed.resetTestingModule();
    element = create(throwError(() => new HttpErrorResponse({ status: 409 }))).nativeElement;
    expect(element.querySelector('[data-contact-invite]')).toBeNull();
    TestBed.resetTestingModule();
    create(of(standing('INVITABLE')), false);
    expect(invitations.standing).not.toHaveBeenCalled();
  });
});

describe('NetworkInvitationsService', () => {
  it('reads a contact\'s standing silently, invites, and reads or declines from the link', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkInvitationsService);
    const http = TestBed.inject(HttpTestingController);
    const at = (method: string, path: string) => http.expectOne(call => call.method === method && call.url.endsWith(path));
    service.standing(7).subscribe();
    at('GET', '/network/invitations/contacts/7');
    service.invite(7).subscribe();
    at('POST', '/network/invitations/contacts/7');
    service.read('a/b').subscribe();
    at('GET', '/public/network-invitations/a%2Fb');
    service.decline('tok').subscribe();
    at('POST', '/public/network-invitations/tok/decline');
    http.verify();
  });
});
