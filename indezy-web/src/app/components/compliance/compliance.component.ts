import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, forkJoin } from 'rxjs';

import { ComplianceDocument, CompliancePack, CompliancePackRequest } from '../../models';
import { ComplianceService } from '../../services/compliance/compliance.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../shared/utils/download-file';
import {
  ComplianceDocumentDialogComponent, ComplianceDocumentDialogData, ComplianceDocumentDialogResult
} from './document-dialog/compliance-document-dialog.component';
import { ComplianceShareDialogComponent, ComplianceShareDialogData } from './share-dialog/compliance-share-dialog.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/** The link just created, shown until the page is left: its token is given only once. */
export interface SharedLink {
  recipient: string;
  url: string;
}

/**
 * The compliance vault: the documents ESNs ask for with when they expire, and the links that share them for a few days,
 * each opening counted, until revoked.
 */
@Component({
  selector: 'app-compliance',
  imports: [PageHeaderComponent, DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatMenuModule, MatProgressSpinnerModule,
    TranslateModule],
  templateUrl: './compliance.component.html',
  styleUrls: ['./compliance.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ComplianceComponent implements OnInit {
  private readonly compliance = inject(ComplianceService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);

  documents: ComplianceDocument[] = [];
  packs: CompliancePack[] = [];
  loading = true;
  sharedLink: SharedLink | null = null;

  ngOnInit(): void {
    forkJoin({ documents: this.compliance.list(), packs: this.compliance.packs() }).subscribe({
      next: ({ documents, packs }) => {
        this.documents = documents;
        this.packs = packs;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.notifications.error('compliance.loadError');
      }
    });
  }

  add(): void {
    this.openDocument({}).subscribe(result => {
      if (!result?.file) {
        return;
      }
      this.compliance.upload(result.file, result.details).subscribe({
        next: document => {
          this.documents = this.sorted([...this.documents, document]);
          this.notifications.success('compliance.added');
        },
        error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'compliance.full' :
          error.status === 400 ? 'compliance.refused' : 'compliance.saveError')
      });
    });
  }

  edit(document: ComplianceDocument): void {
    this.openDocument({ document }).subscribe(result => {
      if (!result) {
        return;
      }
      this.compliance.update(document.id, result.details).subscribe({
        next: saved => {
          this.documents = this.sorted(this.documents.map(item => item.id === saved.id ? saved : item));
          this.notifications.success('compliance.saved');
        },
        error: () => this.notifications.error('compliance.saveError')
      });
    });
  }

  download(document: ComplianceDocument): void {
    this.compliance.content(document.id).subscribe({
      next: blob => saveBlob(blob, document.fileName),
      error: () => this.notifications.error('compliance.downloadError')
    });
  }

  remove(document: ComplianceDocument): void {
    this.confirm.confirm({ titleKey: 'compliance.deleteTitle', messageKey: 'compliance.deleteMessage', confirmKey: 'common.delete',
      danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.compliance.delete(document.id).subscribe({
        next: () => {
          this.documents = this.documents.filter(item => item.id !== document.id);
          this.packs = this.packs.map(pack => ({ ...pack, documentIds: pack.documentIds.filter(id => id !== document.id) }));
          this.notifications.success('compliance.deleted');
        },
        error: () => this.notifications.error('compliance.saveError')
      });
    });
  }

  share(): void {
    this.dialog.open<ComplianceShareDialogComponent, ComplianceShareDialogData, CompliancePackRequest>(ComplianceShareDialogComponent, {
      data: { documents: this.documents }, width: '520px', maxWidth: '95vw'
    }).afterClosed().subscribe(request => {
      if (!request) {
        return;
      }
      this.compliance.share(request).subscribe({
        next: created => {
          this.packs = [created.pack, ...this.packs];
          this.sharedLink = { recipient: created.pack.recipient, url: `${globalThis.location.origin}/documents-pack#${created.token}` };
          this.notifications.success('compliance.linkCreated');
        },
        error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'compliance.tooManyLinks' : 'compliance.saveError')
      });
    });
  }

  copy(link: SharedLink): void {
    navigator.clipboard.writeText(link.url).then(
      () => this.notifications.success('compliance.copied'),
      () => this.notifications.error('compliance.copyError'));
  }

  mailto(link: SharedLink): string {
    return `mailto:?body=${encodeURIComponent(link.url)}`;
  }

  revoke(pack: CompliancePack): void {
    this.confirm.confirm({ titleKey: 'compliance.revokeTitle', messageKey: 'compliance.revokeMessage',
      messageParams: { recipient: pack.recipient }, confirmKey: 'compliance.revoke', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.compliance.revoke(pack.id).subscribe({
        next: revoked => {
          this.packs = this.packs.map(item => item.id === revoked.id ? revoked : item);
          this.notifications.success('compliance.revoked');
        },
        error: () => this.notifications.error('compliance.saveError')
      });
    });
  }

  /** The names of a pack's documents still in the vault. */
  packDocuments(pack: CompliancePack): ComplianceDocument[] {
    return this.documents.filter(document => pack.documentIds.includes(document.id));
  }

  private openDocument(data: ComplianceDocumentDialogData): Observable<ComplianceDocumentDialogResult | undefined> {
    return this.dialog.open<ComplianceDocumentDialogComponent, ComplianceDocumentDialogData, ComplianceDocumentDialogResult>(
      ComplianceDocumentDialogComponent, { data, width: '560px', maxWidth: '95vw' }).afterClosed();
  }

  /** In the order of the types, as the server lists them, the newest first within a type. */
  private sorted(documents: ComplianceDocument[]): ComplianceDocument[] {
    const order = ['URSSAF_VIGILANCE', 'COMPANY_REGISTRATION', 'PROFESSIONAL_INSURANCE', 'BANK_DETAILS', 'IDENTITY', 'OTHER'];
    return [...documents].sort((a, b) => order.indexOf(a.type) - order.indexOf(b.type) || b.createdAt.localeCompare(a.createdAt));
  }
}
