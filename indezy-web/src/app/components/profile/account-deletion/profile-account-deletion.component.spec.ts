import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AccountDeletionService } from '../../../services/account/account-deletion.service';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProfileAccountDeletionComponent } from './profile-account-deletion.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileAccountDeletionComponent', () => {
  let api: jasmine.SpyObj<AccountDeletionService>;
  let auth: jasmine.SpyObj<AuthService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let fixture: ComponentFixture<ProfileAccountDeletionComponent>;
  let navigate: jasmine.Spy;

  function create(email = 'camille@example.test'): HTMLElement {
    api = jasmine.createSpyObj('AccountDeletionService', ['request']);
    auth = jasmine.createSpyObj('AuthService', ['getUser', 'forgetSession']);
    auth.getUser.and.returnValue(email ? { id: 1, email, firstName: 'Camille', lastName: 'Durand' } as never : null);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [ProfileAccountDeletionComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: AccountDeletionService, useValue: api }, { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.returnValue(Promise.resolve(true));
    fixture = TestBed.createComponent(ProfileAccountDeletionComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it("asks for the account's email, then puts it on hold and signs out", () => {
    const element = create();
    (element.querySelector('[data-account-delete]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const confirm = element.querySelector('[data-account-delete-confirm]')!;
    expect(confirm.textContent).toContain('Messages you sent stay with the members you wrote to');
    const submit = element.querySelector('[data-account-delete-submit]') as HTMLButtonElement;
    expect(submit.disabled).toBeTrue();
    fixture.componentInstance.confirmation.setValue('someone@example.test');
    fixture.detectChanges();
    expect(submit.disabled).toBeTrue();
    fixture.componentInstance.delete();
    expect(api.request).not.toHaveBeenCalled();
    fixture.componentInstance.confirmation.setValue(' Camille@Example.test ');
    fixture.detectChanges();
    expect(submit.disabled).toBeFalse();
    api.request.and.returnValue(of({ purgeOn: '2026-12-17' }));
    submit.click();
    expect(api.request).toHaveBeenCalledWith('Camille@Example.test');
    expect(auth.forgetSession).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { onHold: '2026-12-17' } });
  });

  it('says when the account could not be put on hold, and closes without deleting', () => {
    const element = create();
    fixture.componentInstance.ask(true);
    fixture.componentInstance.confirmation.setValue('camille@example.test');
    api.request.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.delete();
    expect(notifications.error).toHaveBeenCalledWith('profile.deletion.error');
    expect(fixture.componentInstance.sending).toBeFalse();
    fixture.detectChanges();
    (element.querySelector('[data-account-delete-cancel]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-account-delete-confirm]')).toBeNull();
    expect(auth.forgetSession).not.toHaveBeenCalled();
  });

  it('confirms nothing without a signed-in account', () => {
    create('');
    fixture.componentInstance.confirmation.setValue('');
    expect(fixture.componentInstance.confirmed).toBeFalse();
  });
});
