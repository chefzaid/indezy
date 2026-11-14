import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, NetworkMembership } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileNetworkComponent } from './profile-network.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileNetworkComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };
  const card: MemberCard = { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null, headline: 'Architecte Java',
    stack: ['Java', 'Kafka'], city: 'Lyon', remote: true, availableFrom: '2026-12-02', availableNow: false, languages: ['Français'] };
  const outside: NetworkMembership = { joined: false, handle: 'camille', fields, card: null };
  const member: NetworkMembership = { joined: true, handle: 'camille', fields, card };

  function create(membership = of(outside)): ComponentFixture<ProfileNetworkComponent> {
    network = jasmine.createSpyObj('NetworkService', ['membership', 'join', 'leave', 'handle']);
    network.membership.and.returnValue(membership);
    network.handle.and.returnValue(of({ handle: 'x', available: true }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [ProfileNetworkComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: NetworkService, useValue: network },
        { provide: NotificationService, useValue: notifications }, { provide: ConfirmDialogService, useValue: confirm }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileNetworkComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('offers the suggested handle, then joins with the fields chosen and shows the card', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(component.form.value.handle).toBe('camille');
    expect(element.textContent).toContain('You are not a member.');
    expect(element.querySelector('[data-network-leave]')).toBeNull();

    network.join.and.returnValue(of(member));
    component.form.controls.fields.patchValue({ location: false, remote: true });
    component.save();
    expect(network.join).toHaveBeenCalledWith('camille', { ...fields, location: false, remote: true });
    expect(notifications.success).toHaveBeenCalledWith('profile.network.joined');
    fixture.detectChanges();
    expect(element.querySelector('[data-network-joined]')?.textContent).toContain('@camille');
    const shown = element.querySelector('[data-member-card="camille"]')!;
    expect(shown.textContent).toContain('Camille Durand');
    expect(shown.textContent).toContain('Lyon');
    expect(shown.textContent).toContain('Remote');
    expect(shown.textContent).toContain('Available from');
    expect(shown.textContent).toContain('Speaks Français');
    expect(shown.querySelector('.initials')?.textContent).toBe('CD');

    component.save();
    expect(notifications.success).toHaveBeenCalledWith('profile.network.saved');
  });

  it('refuses a malformed handle, and says when another member holds it, while typing or on saving', fakeAsync(() => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.form.controls.handle.setValue('Not a handle');
    component.save();
    expect(network.join).not.toHaveBeenCalled();

    network.handle.and.returnValue(of({ handle: 'taken_one', available: false }));
    component.form.controls.handle.setValue('taken_one');
    tick(300);
    expect(component.form.controls.handle.hasError('taken')).toBeTrue();
    network.handle.and.returnValue(of({ handle: 'free_one', available: true }));
    component.form.controls.handle.setValue('free_one');
    tick(300);
    expect(component.form.controls.handle.valid).toBeTrue();

    network.join.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.save();
    expect(component.form.controls.handle.hasError('taken')).toBeTrue();
    component.form.controls.handle.setValue('other_one');
    tick(300);
    network.join.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.save();
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');
  }));

  it('leaves once confirmed, keeping the handle, and says when the membership cannot be read', () => {
    let fixture = create(of(member));
    const component = fixture.componentInstance;
    confirm.confirm.and.returnValue(of(false));
    component.leave();
    expect(network.leave).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.leave.and.returnValue(of(undefined));
    component.leave();
    expect(component.membership).toEqual({ ...member, joined: false, card: null });
    expect(notifications.success).toHaveBeenCalledWith('profile.network.left');
    network.leave.and.returnValue(throwError(() => new Error('down')));
    component.leave();
    expect(notifications.error).toHaveBeenCalledWith('profile.network.saveError');

    TestBed.resetTestingModule();
    fixture = create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('profile.network.loadError');
  });
});
