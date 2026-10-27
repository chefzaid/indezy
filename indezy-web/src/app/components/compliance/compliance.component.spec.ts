import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ComplianceDocument, CompliancePack } from '../../models';
import { ComplianceService } from '../../services/compliance/compliance.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { ComplianceComponent } from './compliance.component';
import en from '../../../assets/i18n/en.json';

describe('ComplianceComponent', () => {
  let service: jasmine.SpyObj<ComplianceService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const document = (id: number, changes: Partial<ComplianceDocument> = {}): ComplianceDocument => ({
    id, type: 'URSSAF_VIGILANCE', label: null, fileName: `doc-${id}.pdf`, contentType: 'application/pdf', sizeBytes: 100,
    issuedOn: '2026-05-16', expiresOn: '2026-11-16', validity: 'EXPIRING', createdAt: `2026-10-0${id}T10:00:00`, ...changes
  });
  const pack = (id: number, changes: Partial<CompliancePack> = {}): CompliancePack => ({
    id, recipient: 'Capgemini', documentIds: [1, 2], createdAt: '2026-10-20T10:00:00', expiresAt: '2026-11-03T10:00:00Z', revokedAt: null,
    active: true, openCount: 2, lastOpenedAt: '2026-10-21T10:00:00Z', ...changes
  });
  const closing = (result: unknown) => dialog.open.and.returnValue({ afterClosed: () => of(result) } as MatDialogRef<unknown>);
  const failure = (status: number) => throwError(() => new HttpErrorResponse({ status }));

  function create(documents = of([document(1), document(2, { type: 'BANK_DETAILS', contentType: 'image/png', expiresOn: null,
    validity: 'NO_EXPIRY', issuedOn: null, label: 'Main account' })]), packs = of([pack(5), pack(6, { active: false, revokedAt: '2026-10-22T10:00:00Z' }),
    pack(7, { active: false })])): ComponentFixture<ComplianceComponent> {
    service = jasmine.createSpyObj('ComplianceService', ['list', 'packs', 'upload', 'update', 'delete', 'content', 'share', 'revoke']);
    service.list.and.returnValue(documents);
    service.packs.and.returnValue(packs);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ComplianceComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), { provide: ComplianceService, useValue: service }, { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificationService, useValue: notifications }]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ComplianceComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the documents with their validity, and the links', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-document="1"]')!.textContent).toContain('URSSAF vigilance certificate');
    expect(element.querySelector('[data-document="1"] [data-document-validity]')!.textContent).toContain('Expires soon');
    expect(element.querySelector('[data-document="2"]')!.textContent).toContain('Bank details (RIB) · Main account');
    expect(element.querySelector('[data-pack="5"]')!.textContent).toContain('opened 2 time(s)');
    expect(element.querySelector('[data-pack="5"] [data-revoke]')).not.toBeNull();
    expect(element.querySelector('[data-pack="6"]')!.textContent).toContain('revoked on');
    expect(element.querySelector('[data-pack="7"]')!.textContent).toContain('expired on');
    expect(fixture.componentInstance.packDocuments(pack(5)).map(item => item.id)).toEqual([1, 2]);
  });

  it('says when there is nothing yet, or when it cannot load', () => {
    const empty = create(of([]), of([]));
    expect((empty.nativeElement as HTMLElement).textContent).toContain('No document yet');
    expect((empty.nativeElement as HTMLElement).querySelector('[data-share-documents]')).toBeNull();
    TestBed.resetTestingModule();
    create(failure(500));
    expect(notifications.error).toHaveBeenCalledWith('compliance.loadError');
  });

  it('adds a document in the order of the types, and says why one is refused', () => {
    const component = create().componentInstance;
    const file = new File(['%PDF'], 'kbis.pdf');
    const details = { type: 'COMPANY_REGISTRATION' as const, label: null, issuedOn: '2026-10-01', expiresOn: null };
    service.upload.and.returnValue(of(document(3, { type: 'COMPANY_REGISTRATION', createdAt: '2026-10-09T10:00:00' })));
    closing({ file, details });
    component.add();
    expect(service.upload).toHaveBeenCalledWith(file, details);
    expect(component.documents.map(item => item.id)).toEqual([1, 3, 2]);
    expect(notifications.success).toHaveBeenCalledWith('compliance.added');

    for (const [status, key] of [[409, 'compliance.full'], [400, 'compliance.refused'], [500, 'compliance.saveError']] as const) {
      service.upload.and.returnValue(failure(status));
      component.add();
      expect(notifications.error).toHaveBeenCalledWith(key);
    }
    closing(undefined);
    component.add();
    expect(service.upload).toHaveBeenCalledTimes(4);
  });

  it('changes, downloads and deletes a document', () => {
    const component = create().componentInstance;
    const details = { type: 'URSSAF_VIGILANCE' as const, label: 'Q4', issuedOn: null, expiresOn: null };
    service.update.and.returnValue(of(document(1, { label: 'Q4' })));
    closing({ file: null, details });
    component.edit(component.documents[0]);
    expect(service.update).toHaveBeenCalledWith(1, details);
    expect(component.documents[0].label).toBe('Q4');
    service.update.and.returnValue(failure(500));
    component.edit(component.documents[0]);
    expect(notifications.error).toHaveBeenCalledWith('compliance.saveError');
    closing(undefined);
    component.edit(component.documents[0]);
    expect(service.update).toHaveBeenCalledTimes(2);

    const link = globalThis.document.createElement('a');
    spyOn(globalThis.document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    service.content.and.returnValue(of(new Blob(['%PDF'])));
    component.download(component.documents[0]);
    expect(link.download).toBe('doc-1.pdf');
    service.content.and.returnValue(failure(404));
    component.download(component.documents[0]);
    expect(notifications.error).toHaveBeenCalledWith('compliance.downloadError');

    service.delete.and.returnValue(of(undefined));
    confirm.confirm.and.returnValue(of(false));
    component.remove(component.documents[1]);
    expect(service.delete).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    component.remove(component.documents[1]);
    expect(component.documents.map(item => item.id)).toEqual([1]);
    expect(component.packs[0].documentIds).toEqual([1]);
    service.delete.and.returnValue(failure(500));
    component.remove(component.documents[0]);
    expect(notifications.error).toHaveBeenCalledWith('compliance.saveError');
  });

  it('shares a link shown once, to copy or email, and revokes it', async () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const request = { recipient: 'Capgemini', documentIds: [1], days: 14 };
    service.share.and.returnValue(of({ pack: pack(8, { documentIds: [1] }), token: 'secret-token' }));
    closing(request);
    component.share();
    expect(service.share).toHaveBeenCalledWith(request);
    expect(component.packs[0].id).toBe(8);
    expect(component.sharedLink).toEqual({ recipient: 'Capgemini', url: `${location.origin}/documents-pack#secret-token` });
    expect(component.mailto(component.sharedLink!)).toBe(`mailto:?body=${encodeURIComponent(component.sharedLink!.url)}`);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-shared-link]')!.textContent).toContain('secret-token');

    const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
    component.copy(component.sharedLink!);
    await Promise.resolve();
    expect(writeText).toHaveBeenCalledWith(component.sharedLink!.url);
    expect(notifications.success).toHaveBeenCalledWith('compliance.copied');
    writeText.and.rejectWith(new Error('denied'));
    component.copy(component.sharedLink!);
    await Promise.resolve();
    await Promise.resolve();
    expect(notifications.error).toHaveBeenCalledWith('compliance.copyError');

    service.share.and.returnValue(failure(409));
    component.share();
    expect(notifications.error).toHaveBeenCalledWith('compliance.tooManyLinks');
    service.share.and.returnValue(failure(500));
    component.share();
    expect(notifications.error).toHaveBeenCalledWith('compliance.saveError');
    closing(undefined);
    component.share();
    expect(service.share).toHaveBeenCalledTimes(3);

    service.revoke.and.returnValue(of(pack(8, { active: false, revokedAt: '2026-10-27T10:00:00Z' })));
    confirm.confirm.and.returnValue(of(false));
    component.revoke(component.packs[0]);
    expect(service.revoke).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    component.revoke(component.packs[0]);
    expect(component.packs[0].active).toBeFalse();
    expect(notifications.success).toHaveBeenCalledWith('compliance.revoked');
    service.revoke.and.returnValue(failure(500));
    component.revoke(component.packs[0]);
    expect(notifications.error).toHaveBeenCalledWith('compliance.saveError');
  });
});
