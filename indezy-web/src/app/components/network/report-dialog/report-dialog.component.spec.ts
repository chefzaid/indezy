import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ReportDialogComponent, ReportSubject } from './report-dialog.component';
import en from '../../../../assets/i18n/en.json';

describe('ReportDialogComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ReportDialogComponent, boolean>>;

  function create(subject: ReportSubject = { target: 'posts', id: 4 }) {
    network = jasmine.createSpyObj('NetworkService', ['report']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [ReportDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: NetworkService, useValue: network },
        { provide: NotificationService, useValue: notifications }, { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: subject }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ReportDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('asks for a reason, then sends the report with the words typed, if any', () => {
    const fixture = create({ target: 'members', id: 'camille' });
    const component = fixture.componentInstance;
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Report this member');
    component.send();
    expect(network.report).not.toHaveBeenCalled();
    network.report.and.returnValue(of(undefined));
    component.form.setValue({ reason: 'SPAM', details: '  ' });
    component.send();
    expect(network.report).toHaveBeenCalledWith('members', 'camille', 'SPAM', null);
    expect(notifications.success).toHaveBeenCalledWith('network.report.sent');
    expect(dialogRef.close).toHaveBeenCalledWith(true);
    component.send();
    expect(network.report).toHaveBeenCalledTimes(1);
    component.sending = false;
    component.form.setValue({ reason: 'OTHER', details: ' Faux profil ' });
    component.send();
    expect(network.report).toHaveBeenCalledWith('members', 'camille', 'OTHER', 'Faux profil');
  });

  it('says when it was already reported, when the member is suspended, or when it cannot be sent', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.form.setValue({ reason: 'HATE', details: '' });
    const refusal = (status: number, message?: string) => throwError(() => new HttpErrorResponse({ status, error: { message } }));
    network.report.and.returnValue(refusal(409, 'You already reported this: the moderators will look at it'));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('network.report.already');
    expect(dialogRef.close).toHaveBeenCalledWith(false);
    network.report.and.returnValue(refusal(409, 'The moderators suspended you until 18 November 2026, 10:00'));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('network.report.suspended');
    network.report.and.returnValue(refusal(500));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('network.report.error');
    expect(component.sending).toBeFalse();
  });

  it('opens with what is reported and emits whether it was sent', () => {
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(true) } as never);
    let sent: boolean | undefined;
    ReportDialogComponent.open(dialog, { target: 'comments', id: 7 }).subscribe(result => sent = result);
    expect(dialog.open).toHaveBeenCalledWith(ReportDialogComponent, { data: { target: 'comments', id: 7 } });
    expect(sent).toBeTrue();
  });
});
