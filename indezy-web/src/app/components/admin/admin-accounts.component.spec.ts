import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AdminAccount, AdminAccountPage } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { AdminAccountsComponent } from './admin-accounts.component';
import en from '../../../assets/i18n/en.json';

describe('AdminAccountsComponent', () => {
  let admin: jasmine.SpyObj<AdminService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const alice: AdminAccount = { id: 1, email: 'alice@example.com', firstName: 'Alice', lastName: 'Martin',
    createdAt: '2026-01-05T10:00:00Z', disabledAt: null, deleted: false, admin: false };
  const chief: AdminAccount = { ...alice, id: 2, email: 'chief@example.com', firstName: 'Chief', admin: true };
  const page = (accounts: AdminAccount[]): AdminAccountPage => ({ accounts, total: accounts.length, page: 0, size: 20 });

  function create(): ComponentFixture<AdminAccountsComponent> {
    TestBed.configureTestingModule({
      imports: [AdminAccountsComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: AdminService, useValue: admin },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(AdminAccountsComponent);
    fixture.detectChanges();
    return fixture;
  }

  function rendered(fixture: ComponentFixture<AdminAccountsComponent>): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  beforeEach(() => {
    admin = jasmine.createSpyObj('AdminService', ['accounts', 'setDisabled']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    admin.accounts.and.returnValue(of(page([alice, chief])));
  });

  it('lists the newest accounts, with no action on an administrator', fakeAsync(() => {
    const fixture = create();
    tick(250);

    expect(admin.accounts).toHaveBeenCalledWith('', 0);
    const element = rendered(fixture);
    expect(element.querySelectorAll('[data-account]').length).toBe(2);
    expect(element.querySelector('[data-account="1"] button')?.textContent).toContain('Disable');
    expect(element.querySelector('[data-account="2"] button')).toBeNull();
    expect(element.querySelector('[data-account="2"]')?.textContent).toContain('Admin');
  }));

  it('searches once the typing pauses, and turns pages', fakeAsync(() => {
    const fixture = create();
    tick(250);
    const component = fixture.componentInstance;
    component.query = ' ali ';
    component.search();
    component.search();
    tick(250);
    component.pageChanged({ pageIndex: 1, pageSize: 20, length: 40 });
    tick(250);

    expect(admin.accounts.calls.allArgs()).toEqual([['', 0], ['ali', 0], ['ali', 1]]);
  }));

  it('says when no account matches', fakeAsync(() => {
    admin.accounts.and.returnValue(of(page([])));
    const fixture = create();
    tick(250);

    expect(rendered(fixture).textContent).toContain('No account matches this search.');
  }));

  it('disables an account once confirmed and shows its new state', fakeAsync(() => {
    const fixture = create();
    tick(250);
    confirm.confirm.and.returnValue(of(true));
    admin.setDisabled.and.returnValue(of({ ...alice, disabledAt: '2026-10-01T08:00:00Z' }));

    fixture.componentInstance.toggle(alice);

    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageParams: { name: 'Alice Martin' }, danger: true }));
    expect(admin.setDisabled).toHaveBeenCalledWith(1, true);
    expect(notifications.success).toHaveBeenCalledWith('admin.disabledDone');
    expect(rendered(fixture).querySelector('[data-account="1"]')?.textContent).toContain('Disabled since');
  }));

  it('enables a disabled account again, named by email when it has no name', fakeAsync(() => {
    const disabled = { ...alice, firstName: '', lastName: '', disabledAt: '2026-10-01T08:00:00Z' };
    admin.accounts.and.returnValue(of(page([disabled])));
    const fixture = create();
    tick(250);
    confirm.confirm.and.returnValue(of(true));
    admin.setDisabled.and.returnValue(of({ ...disabled, disabledAt: null }));

    fixture.componentInstance.toggle(disabled);

    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ messageParams: { name: 'alice@example.com' }, danger: false }));
    expect(admin.setDisabled).toHaveBeenCalledWith(1, false);
    expect(notifications.success).toHaveBeenCalledWith('admin.enabledDone');
  }));

  it('changes nothing when the confirmation is cancelled or the save fails', fakeAsync(() => {
    const fixture = create();
    tick(250);
    confirm.confirm.and.returnValue(of(false));
    fixture.componentInstance.toggle(alice);
    expect(admin.setDisabled).not.toHaveBeenCalled();

    confirm.confirm.and.returnValue(of(true));
    admin.setDisabled.and.returnValue(throwError(() => new Error('409')));
    fixture.componentInstance.toggle(alice);
    expect(notifications.error).toHaveBeenCalledWith('admin.saveError');
    expect(fixture.componentInstance.result?.accounts[0].disabledAt).toBeNull();
  }));

  it('says when the accounts cannot be read', fakeAsync(() => {
    admin.accounts.and.returnValue(throwError(() => new Error('500')));
    create();
    tick(250);

    expect(notifications.error).toHaveBeenCalledWith('admin.loadError');
  }));
});
