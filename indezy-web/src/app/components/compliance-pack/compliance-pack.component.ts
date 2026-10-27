import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { PublicComplianceDocument, PublicCompliancePack } from '../../models';
import { ComplianceService } from '../../services/compliance/compliance.service';
import { NotificationService } from '../../services/notification/notification.service';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';
import { saveBlob } from '../../shared/utils/download-file';

/**
 * The page an ESN opens from a shared documents link, without an account: who shares them, until when, and each
 * document to download. The token comes after the # of the link, so it never reaches a server log.
 */
@Component({
  selector: 'app-compliance-pack',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, TranslateModule, PublicPageHeaderComponent],
  templateUrl: './compliance-pack.component.html',
  styleUrls: ['./compliance-pack.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CompliancePackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly compliance = inject(ComplianceService);
  private readonly notifications = inject(NotificationService);

  pack: PublicCompliancePack | null = null;
  loading = true;
  private token = '';

  ngOnInit(): void {
    this.token = (this.route.snapshot.fragment ?? '').trim();
    if (!this.token) {
      this.loading = false;
      return;
    }
    this.compliance.publicPack(this.token).subscribe({
      next: pack => {
        this.pack = pack;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  download(document: PublicComplianceDocument): void {
    this.compliance.publicDocument(this.token, document.id).subscribe({
      next: blob => saveBlob(blob, document.fileName),
      error: () => this.notifications.error('compliance.pack.downloadError')
    });
  }
}
