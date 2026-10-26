import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { ProjectAttachment } from '../../../models/project.models';
import { ProjectAttachmentService } from '../../../services/project/project-attachment.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { AttachmentPreviewDialogComponent } from './attachment-preview-dialog.component';
import { ProjectAttachmentsComponent } from './project-attachments.component';
import en from '../../../../assets/i18n/en.json';

describe('ProjectAttachmentsComponent', () => {
  let attachments: jasmine.SpyObj<ProjectAttachmentService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  const brief: ProjectAttachment = { id: 1, fileName: 'Job brief.pdf', contentType: 'application/pdf', sizeBytes: 250_000,
    createdAt: '2026-10-20T10:00:00', inline: true };
  const contract: ProjectAttachment = { id: 2, fileName: 'Contract.docx', sizeBytes: 900,
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', createdAt: '2026-10-21T10:00:00', inline: false };

  function create(list = of([brief, contract])): ComponentFixture<ProjectAttachmentsComponent> {
    attachments = jasmine.createSpyObj('ProjectAttachmentService', ['list', 'upload', 'content', 'delete']);
    attachments.list.and.returnValue(list);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error', 'errorText']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [ProjectAttachmentsComponent, TranslateModule.forRoot()],
      providers: [
        { provide: ProjectAttachmentService, useValue: attachments },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: MatDialog, useValue: dialog }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProjectAttachmentsComponent);
    fixture.componentRef.setInput('projectId', 8);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProjectAttachmentsComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function picked(file: File): HTMLInputElement {
    const input = document.createElement('input');
    input.type = 'file';
    const files = new DataTransfer();
    files.items.add(file);
    input.files = files.files;
    return input;
  }

  it('lists the opportunity\'s files with their size and date', () => {
    const fixture = create();

    expect(attachments.list).toHaveBeenCalledWith(8);
    expect(text(fixture)).toContain('Job brief.pdf244 KB · Oct 20, 2026');
    expect(text(fixture)).toContain('Contract.docx900 B');
    expect(fixture.componentInstance.icon(brief)).toBe('picture_as_pdf');
    expect(fixture.componentInstance.icon(contract)).toBe('description');
    expect(fixture.componentInstance.size(3_500_000)).toBe('3.3 MB');
  });

  it('invites to attach a first file, and says when the list cannot be loaded', () => {
    expect(text(create(of([])))).toContain('No files yet');
    TestBed.resetTestingModule();
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('projects.attachments.loadError');
  });

  it('shows a PDF in the page and downloads a Word file', () => {
    const component = create().componentInstance;
    const blob = new Blob(['%PDF']);
    attachments.content.and.returnValue(of(blob));
    const click = spyOn(HTMLAnchorElement.prototype, 'click');

    component.open(brief);
    component.open(contract);
    component.download(brief);

    expect(dialog.open).toHaveBeenCalledOnceWith(AttachmentPreviewDialogComponent, jasmine.objectContaining({
      data: { fileName: 'Job brief.pdf', contentType: 'application/pdf', blob }
    }));
    expect(click).toHaveBeenCalledTimes(2);
  });

  it('attaches a picked file at the top of the list', () => {
    const fixture = create();
    const added = { ...brief, id: 3, fileName: 'Test.pdf' };
    attachments.upload.and.returnValue(of(added));
    const input = picked(new File(['%PDF'], 'Test.pdf'));

    fixture.componentInstance.upload(input);

    expect(attachments.upload).toHaveBeenCalledWith(8, jasmine.any(File));
    expect(input.value).toBe('');
    expect(fixture.componentInstance.attachments.map(a => a.id)).toEqual([3, 1, 2]);
    expect(notifications.success).toHaveBeenCalledWith('projects.attachments.uploaded');
  });

  it('refuses a file over 10 MB before sending it, and explains what the server refuses', () => {
    const component = create().componentInstance;
    component.upload(picked(new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'Huge.pdf')));
    expect(attachments.upload).not.toHaveBeenCalled();
    expect(notifications.errorText).toHaveBeenCalledWith('"Huge.pdf" is larger than 10 MB');

    attachments.upload.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 400 })),
      throwError(() => new HttpErrorResponse({ status: 409 })),
      throwError(() => new HttpErrorResponse({ status: 500 })));
    for (let i = 0; i < 3; i++) {
      component.upload(picked(new File(['x'], 'page.html')));
    }
    expect(notifications.errorText.calls.mostRecent().args[0]).toContain('"page.html" could not be attached');
    expect(notifications.error.calls.allArgs()).toEqual([['projects.attachments.limit'], ['projects.attachments.uploadError']]);
    expect(component.uploading).toBeFalse();
  });

  it('sends one file at a time and nothing when no file is picked', () => {
    const component = create().componentInstance;
    attachments.upload.and.returnValue(new Subject<ProjectAttachment>());
    component.upload(picked(new File(['%PDF'], 'a.pdf')));
    component.upload(picked(new File(['%PDF'], 'b.pdf')));
    component.upload(document.createElement('input'));

    expect(attachments.upload).toHaveBeenCalledTimes(1);
  });

  it('deletes a file once confirmed, and says when opening or deleting fails', () => {
    const component = create().componentInstance;
    confirm.confirm.and.returnValues(of(false), of(true), of(true));
    attachments.delete.and.returnValues(of(undefined), throwError(() => new Error('down')));
    attachments.content.and.returnValue(throwError(() => new Error('down')));

    component.remove(brief);
    component.remove(brief);
    component.remove(contract);
    component.open(contract);
    component.download(contract);

    expect(attachments.delete.calls.allArgs()).toEqual([[1], [2]]);
    expect(component.attachments.map(a => a.id)).toEqual([2]);
    expect(notifications.error.calls.allArgs()).toEqual([
      ['projects.attachments.deleteError'], ['projects.attachments.openError'], ['projects.attachments.openError']
    ]);
  });
});
