import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectDto, ProjectStatus } from '../../../models';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MissionEnd, MissionEndDialogComponent } from './mission-end-dialog.component';

/**
 * Ends a won mission, asking when and why; once ended, says when and why, and resumes it if it was ended by mistake.
 * Shows nothing for an opportunity that is not a won mission.
 */
@Component({
  selector: 'app-mission-end-action',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule],
  template: `
    @if (project.status === won) {
      @if (project.endedOn) {
        <span class="ended" data-mission-ended>
          <mat-icon inline aria-hidden="true">flag</mat-icon>
          {{ 'missionEnd.ended' | translate:{ date: (project.endedOn | date:'mediumDate') } }}
          @if (project.endReason) {
            <span class="reason">· {{ project.endReason }}</span>
          }
        </span>
        <button mat-button type="button" (click)="resume()" [disabled]="busy" data-mission-resume>
          <mat-icon>replay</mat-icon>
          {{ 'missionEnd.resume' | translate }}
        </button>
      } @else {
        <button mat-stroked-button type="button" (click)="end()" [disabled]="busy" [matTooltip]="'missionEnd.hint' | translate"
          data-mission-end>
          <mat-icon>flag</mat-icon>
          {{ 'missionEnd.action' | translate }}
        </button>
      }
    }
  `,
  styles: [`
    :host { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; min-width: 0; }
    .ended { display: inline-flex; align-items: center; gap: 4px; flex-wrap: wrap; font-size: 0.875rem; color: var(--app-text-muted); }
    .reason { overflow-wrap: anywhere; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MissionEndActionComponent {
  @Input({ required: true }) project!: ProjectDto;
  /** The mission as saved, ended or resumed. */
  @Output() readonly changed = new EventEmitter<ProjectDto>();
  readonly won = ProjectStatus.WON;
  busy = false;

  private readonly dialog = inject(MatDialog);
  private readonly projectService = inject(ProjectService);
  private readonly notificationService = inject(NotificationService);

  end(): void {
    if (!this.project.id || this.busy) {
      return;
    }
    const projectId = this.project.id;
    this.dialog.open<MissionEndDialogComponent, unknown, MissionEnd>(MissionEndDialogComponent, {
      data: { role: this.project.role, startDate: this.project.startDate }
    }).afterClosed().subscribe(missionEnd => {
      if (!missionEnd) {
        return;
      }
      this.busy = true;
      this.projectService.endMission(projectId, missionEnd.endedOn, missionEnd.reason).subscribe({
        next: saved => this.saved(saved, 'missionEnd.endedSuccess'),
        error: () => this.failed('missionEnd.endError')
      });
    });
  }

  resume(): void {
    if (!this.project.id || this.busy) {
      return;
    }
    this.busy = true;
    this.projectService.resumeMission(this.project.id).subscribe({
      next: saved => this.saved(saved, 'missionEnd.resumedSuccess'),
      error: () => this.failed('missionEnd.resumeError')
    });
  }

  private saved(saved: ProjectDto, messageKey: string): void {
    this.busy = false;
    this.notificationService.success(messageKey);
    this.changed.emit(saved);
  }

  private failed(messageKey: string): void {
    this.busy = false;
    this.notificationService.error(messageKey);
  }
}
