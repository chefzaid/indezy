import { Component, Input, inject } from '@angular/core';
import { DOCUMENT, Location } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService } from '../../../services/notification/notification.service';

@Component({
  selector: 'app-project-link',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule],
  template: `
    <button mat-icon-button type="button" (click)="copy($event)"
      [attr.aria-label]="'projects.share.copy' | translate" [matTooltip]="'projects.share.copy' | translate">
      <mat-icon>link</mat-icon>
    </button>
  `
})
export class ProjectLinkComponent {
  @Input({ required: true }) projectId!: number;
  private readonly clipboard = inject(Clipboard);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly document = inject(DOCUMENT);
  private readonly notifications = inject(NotificationService);

  copy(event: Event): void {
    event.stopPropagation();
    const route = this.router.createUrlTree(['/projects'], { queryParams: { project: this.projectId } });
    const path = this.location.prepareExternalUrl(this.router.serializeUrl(route));
    const url = new URL(path, this.document.baseURI).href;
    if (this.clipboard.copy(url)) {
      this.notifications.success('projects.share.copied');
    } else {
      this.notifications.error('projects.share.failed');
    }
  }
}
