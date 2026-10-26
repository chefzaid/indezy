import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ProjectAttachment } from '../../../models/project.models';
import { ProjectAttachmentService } from '../../../services/project/project-attachment.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../../shared/utils/download-file';
import { AttachmentPreviewDialogComponent } from './attachment-preview-dialog.component';

/** The accepted files, for the file picker; the server checks their content. */
const ACCEPTED = '.pdf,.png,.jpg,.jpeg,.gif,.webp,.docx,.odt,.txt,.md';

/**
 * The files kept with an opportunity: attached one at a time, PDF and images shown in the page, anything downloaded,
 * and deleted after a confirmation.
 */
@Component({
  selector: 'app-project-attachments',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule, TranslateModule],
  templateUrl: './project-attachments.component.html',
  styleUrls: ['./project-attachments.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProjectAttachmentsComponent implements OnInit {
  @Input({ required: true }) projectId!: number;

  readonly accepted = ACCEPTED;
  attachments: ProjectAttachment[] = [];
  uploading = false;

  constructor(
    private readonly attachmentService: ProjectAttachmentService,
    private readonly notificationService: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly translate: TranslateService,
    private readonly dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.attachmentService.list(this.projectId).subscribe({
      next: attachments => this.attachments = attachments,
      error: () => this.notificationService.error('projects.attachments.loadError')
    });
  }

  /** Attaches the picked file, checked for its size before it is sent. */
  upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.uploading) {
      return;
    }
    if (file.size > ProjectAttachmentService.MAX_BYTES) {
      this.notificationService.errorText(this.translate.instant('projects.attachments.tooLarge', { name: file.name }));
      return;
    }
    this.uploading = true;
    this.attachmentService.upload(this.projectId, file).subscribe({
      next: attachment => {
        this.uploading = false;
        this.attachments = [attachment, ...this.attachments];
        this.notificationService.success('projects.attachments.uploaded');
      },
      error: (error: HttpErrorResponse) => {
        this.uploading = false;
        if (error.status === 400) {
          this.notificationService.errorText(this.translate.instant('projects.attachments.refused', { name: file.name }));
        } else {
          this.notificationService.error(error.status === 409 ? 'projects.attachments.limit' : 'projects.attachments.uploadError');
        }
      }
    });
  }

  /** Shows a PDF or an image in the page, downloads anything else. */
  open(attachment: ProjectAttachment): void {
    this.attachmentService.content(attachment.id).subscribe({
      next: blob => {
        if (attachment.inline) {
          this.dialog.open(AttachmentPreviewDialogComponent, {
            data: { fileName: attachment.fileName, contentType: attachment.contentType, blob }, maxWidth: '95vw'
          });
        } else {
          saveBlob(blob, attachment.fileName);
        }
      },
      error: () => this.notificationService.error('projects.attachments.openError')
    });
  }

  download(attachment: ProjectAttachment): void {
    this.attachmentService.content(attachment.id).subscribe({
      next: blob => saveBlob(blob, attachment.fileName),
      error: () => this.notificationService.error('projects.attachments.openError')
    });
  }

  remove(attachment: ProjectAttachment): void {
    this.confirmDialog.confirm({
      messageKey: 'projects.attachments.deleteConfirm', messageParams: { name: attachment.fileName }, danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.attachmentService.delete(attachment.id).subscribe({
        next: () => {
          this.attachments = this.attachments.filter(item => item.id !== attachment.id);
          this.notificationService.success('projects.attachments.deleted');
        },
        error: () => this.notificationService.error('projects.attachments.deleteError')
      });
    });
  }

  icon(attachment: ProjectAttachment): string {
    return attachment.contentType === 'application/pdf' ? 'picture_as_pdf' :
      attachment.contentType.startsWith('image/') ? 'image' : 'description';
  }

  /** A size as people read it: bytes, KB or MB. */
  size(bytes: number): string {
    return bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
