import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { UserNotificationSettings } from '../../../models/user-management.models';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferAlertsService } from '../../../services/offer/offer-alerts.service';
import { ProfileNotificationsComponent } from './profile-notifications.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileNotificationsComponent', () => {
  let users: jasmine.SpyObj<UserManagementService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let alerts: jasmine.SpyObj<OfferAlertsService>;
  const settings: UserNotificationSettings = { weeklyReports: false, newOffersBadge: true, emailsAvailable: true };

  function create(read = of(settings)): ComponentFixture<ProfileNotificationsComponent> {
    users.getNotificationSettings.and.returnValue(read);
    TestBed.configureTestingModule({
      imports: [ProfileNotificationsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications },
        { provide: OfferAlertsService, useValue: alerts }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileNotificationsComponent);
    fixture.detectChanges();
    return fixture;
  }

  const change = (checked: boolean): MatSlideToggleChange => ({ checked, source: { checked } as MatSlideToggle });

  beforeEach(() => {
    users = jasmine.createSpyObj('UserManagementService', ['getNotificationSettings', 'updateNotificationSettings']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    alerts = jasmine.createSpyObj('OfferAlertsService', ['start', 'stop']);
  });

  it('shows the switches that do something, and says when emails cannot be sent', () => {
    const fixture = create(of({ ...settings, emailsAvailable: false }));
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelectorAll('[data-switch]').length).toBe(2);
    expect(element.textContent).toContain('Email me a weekly digest');
    expect(element.querySelector('[data-emails-unavailable]')).not.toBeNull();
    expect(element.querySelector('a[href="/offers"]')).not.toBeNull();
  });

  it('turns the weekly digest on at once', () => {
    const component = create().componentInstance;
    users.updateNotificationSettings.and.returnValue(of({ ...settings, weeklyReports: true }));

    component.changed('weeklyReports', change(true));

    expect(users.updateNotificationSettings).toHaveBeenCalledWith({ weeklyReports: true });
    expect(component.settings?.weeklyReports).toBeTrue();
    expect(alerts.start).not.toHaveBeenCalled();
    expect(alerts.stop).not.toHaveBeenCalled();
    expect(notifications.success).toHaveBeenCalledWith('profile.notificationsUpdated');
  });

  it('takes the badge off the menu and puts it back', () => {
    const component = create().componentInstance;
    users.updateNotificationSettings.and.returnValue(of({ ...settings, newOffersBadge: false }));
    component.changed('newOffersBadge', change(false));
    expect(alerts.stop).toHaveBeenCalled();

    users.updateNotificationSettings.and.returnValue(of(settings));
    component.changed('newOffersBadge', change(true));
    expect(alerts.start).toHaveBeenCalled();
  });

  it('puts the switch back when it cannot be saved, and says when the settings cannot be read', () => {
    const component = create().componentInstance;
    users.updateNotificationSettings.and.returnValue(throwError(() => new Error('500')));
    const failed = change(true);

    component.changed('weeklyReports', failed);

    expect(failed.source.checked).toBeFalse();
    expect(component.settings).toEqual(settings);
    expect(notifications.error).toHaveBeenCalledWith('profile.errors.updatingNotifications');

    TestBed.resetTestingModule();
    create(throwError(() => new Error('500')));
    expect(notifications.error).toHaveBeenCalledWith('profile.errors.loadingNotifications');
  });
});
