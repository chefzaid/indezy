import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { NEVER, of, throwError } from 'rxjs';

import { IMPORT_FILE_MAX_BYTES, ProfileDataImportComponent } from './profile-data-import.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { WorkspaceImportReport, WorkspaceImportSection } from '../../../models/user-management.models';

describe('ProfileDataImportComponent', () => {
  let fixture: ComponentFixture<ProfileDataImportComponent>;
  let component: ProfileDataImportComponent;
  let users: jasmine.SpyObj<UserManagementService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  const section = (over: Partial<WorkspaceImportSection>): WorkspaceImportSection =>
    ({ kind: 'CLIENTS', total: 0, added: 0, duplicates: 0, invalid: 0, items: [], ...over });
  const preview: WorkspaceImportReport = { applied: false, sections: [
    section({ kind: 'CLIENTS', total: 3, added: 1, duplicates: 1, invalid: 1, items: [
      { label: 'Acme', status: 'NEW' }, { label: 'Alten', status: 'DUPLICATE' }, { label: '—', status: 'INVALID', reason: 'City is required' }
    ] }),
    section({ kind: 'SEASONS' })
  ] };
  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const choose = (file?: File): void => {
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('[data-import-file]')!;
    Object.defineProperty(input, 'files', { value: file ? [file] : [], configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const exportFile = (): File => new File(['{"clients": []}'], 'user-data.json', { type: 'application/json' });

  beforeEach(async () => {
    users = jasmine.createSpyObj<UserManagementService>('UserManagementService', ['previewImport', 'importData']);
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['success', 'error']);
    users.previewImport.and.returnValue(of(preview));
    users.importData.and.returnValue(of({ ...preview, applied: true }));
    await TestBed.configureTestingModule({
      imports: [ProfileDataImportComponent, TranslateModule.forRoot()],
      providers: [{ provide: UserManagementService, useValue: users }, { provide: NotificationService, useValue: notifications }]
    }).compileComponents();
    fixture = TestBed.createComponent(ProfileDataImportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('previews the chosen file, listing what is left out first and only counting what is there', () => {
    const file = exportFile();
    choose(file);

    expect(users.previewImport).toHaveBeenCalledWith(file);
    expect(component.report).toBe(preview);
    expect(component.canImport).toBeTrue();
    const sections = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-section]');
    expect(sections.length).toBe(1);
    expect(component.listed(preview.sections[0]).map(item => item.label)).toEqual(['—', 'Acme']);
    expect(text()).toContain('profile.import.leftOut');
    expect(users.importData).not.toHaveBeenCalled();
  });

  it('imports the previewed file once and reports what was added', () => {
    choose(exportFile());
    users.importData.and.returnValue(NEVER);
    component.confirm();
    component.confirm();
    expect(users.importData).toHaveBeenCalledTimes(1);

    component.isBusy = false;
    users.importData.and.returnValue(of({ ...preview, applied: true }));
    choose(exportFile());
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-import-confirm]')!.click();
    fixture.detectChanges();
    expect(component.report?.applied).toBeTrue();
    expect(component.file).toBeUndefined();
    expect(notifications.success).toHaveBeenCalledWith('profile.import.done');
    expect(component.canImport).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-import-confirm]')).toBeNull();

    component.cancel();
    expect(component.report).toBeUndefined();
  });

  it('offers nothing to import when everything is already there', () => {
    users.previewImport.and.returnValue(of({ applied: false, sections: [section({ total: 2, duplicates: 2 })] }));
    choose(exportFile());

    expect(component.canImport).toBeFalse();
    expect(text()).toContain('profile.import.nothingNew');
    component.confirm();
    expect(users.importData).not.toHaveBeenCalled();
  });

  it('refuses a file over the limit, ignores no file, and tells an unreadable file from a failure', () => {
    choose(new File([new Uint8Array(IMPORT_FILE_MAX_BYTES + 1)], 'big.json'));
    expect(notifications.error).toHaveBeenCalledWith('profile.import.tooLarge');
    choose();
    expect(users.previewImport).not.toHaveBeenCalled();

    users.previewImport.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    choose(exportFile());
    expect(notifications.error).toHaveBeenCalledWith('profile.import.invalidFile');
    expect(component.report).toBeUndefined();

    users.previewImport.and.returnValue(of(preview));
    choose(exportFile());
    users.importData.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.confirm();
    expect(notifications.error).toHaveBeenCalledWith('profile.import.error');
    expect(component.isBusy).toBeFalse();
    expect(component.file).toBeUndefined();
  });
});
