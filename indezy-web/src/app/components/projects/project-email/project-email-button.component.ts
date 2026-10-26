import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectDto } from '../../../models';
import { ProjectEmailDialogComponent } from './project-email-dialog.component';

/** Opens the email composer for an opportunity. */
@Component({
  selector: 'app-project-email-button',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  template: `
    <button mat-button type="button" (click)="open()">
      <mat-icon>mail</mat-icon>
      {{ 'emails.action' | translate }}
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProjectEmailButtonComponent {
  @Input({ required: true }) project!: ProjectDto;

  constructor(private readonly dialog: MatDialog) {}

  open(): void {
    this.dialog.open(ProjectEmailDialogComponent, { data: this.project, autoFocus: 'first-tabbable', maxWidth: '95vw' });
  }
}
