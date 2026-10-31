import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { NonSolicitation } from '../../../../models/project.models';
import { ProjectService } from '../../../../services/project/project.service';

/** Warns that a won mission's non-solicitation clause covers this opportunity, naming the ESN and when the clause ends. */
@Component({
  selector: 'app-non-solicitation-notice',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DatePipe, RouterLink, MatIconModule, TranslateModule],
  template: `
    @for (clause of clauses; track clause.missionId) {
      <p class="notice" role="note" data-non-solicitation>
        <mat-icon aria-hidden="true">gavel</mat-icon>
        <span>
          {{ 'projects.nonSolicitation.notice' | translate:{ client: clause.clientName, esn: clause.middlemanName } }}
          @if (clause.endsOn) {
            {{ 'projects.nonSolicitation.until' | translate:{ date: (clause.endsOn | date:'longDate') } }}
          } @else {
            {{ 'projects.nonSolicitation.afterMission' | translate:{ months: clause.months } }}
          }
          · <a [routerLink]="['/projects', clause.missionId]">{{ clause.missionRole }}</a>
        </span>
      </p>
    }
  `,
  styles: `
    .notice {
      display: flex; gap: 8px; align-items: flex-start; margin: 12px 0 0; padding: 8px 12px; border-radius: 8px;
      background: var(--app-info-bg); color: var(--app-text);
    }
    mat-icon { color: var(--app-danger); flex-shrink: 0; }
    a { color: var(--app-link); }
  `
})
export class NonSolicitationNoticeComponent implements OnChanges {
  private readonly projectService = inject(ProjectService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) projectId!: number;
  @Input({ required: true }) freelanceId!: number;

  clauses: NonSolicitation[] = [];

  ngOnChanges(): void {
    this.projectService.getNonSolicitations(this.freelanceId).subscribe({
      next: clauses => {
        this.clauses = clauses.filter(clause => clause.projectId === this.projectId);
        this.changes.markForCheck();
      },
      error: () => {
        this.clauses = [];
        this.changes.markForCheck();
      }
    });
  }
}
