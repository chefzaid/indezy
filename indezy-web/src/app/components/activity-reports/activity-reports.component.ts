import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectDto, ProjectStatus } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { LoadingComponent } from '../../shared/components/loading/loading.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { fromIsoDate, toIsoDate } from '../../shared/locale/app-locale';
import { ActivityReportCardComponent } from '../projects/activity-report-card/activity-report-card.component';
import { MissionEndActionComponent } from '../projects/mission-end-action/mission-end-action.component';

/**
 * The missions in progress, each with its activity report of the month to fill in and send, and the button that ends
 * it once it is over (`activeMissions` says which).
 */
@Component({
  selector: 'app-activity-reports',
  imports: [DatePipe, RouterModule, MatButtonModule, MatIconModule, TranslateModule, LoadingComponent, PageHeaderComponent,
    ActivityReportCardComponent, MissionEndActionComponent],
  templateUrl: './activity-reports.component.html',
  styleUrls: ['./activity-reports.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ActivityReportsComponent implements OnInit {
  missions: ProjectDto[] = [];
  isLoading = true;

  private readonly authService = inject(AuthService);
  private readonly projectService = inject(ProjectService);
  private readonly notificationService = inject(NotificationService);

  ngOnInit(): void {
    const freelanceId = this.authService.getUser()?.id;
    if (!freelanceId) {
      this.isLoading = false;
      return;
    }
    this.projectService.getByFreelanceId(freelanceId).subscribe({
      next: projects => {
        this.missions = activeMissions(projects, new Date());
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.notificationService.error('activityReportsPage.loadError');
      }
    });
  }

  /** A mission ended or resumed here stays listed until the next visit, so its last report can still be sent. */
  onChanged(saved: ProjectDto): void {
    this.missions = this.missions.map(mission => mission.id === saved.id ? saved : mission);
  }

  /** The day the mission ended, else the last day of its duration (yyyy-MM-dd), if known. */
  lastDay(mission: ProjectDto): string | undefined {
    return lastDayOf(mission);
  }

  /** Its planned end has passed but it was not ended: likely over, or renewed without its duration updated. */
  plannedEndPassed(mission: ProjectDto): boolean {
    const last = lastDayOf(mission);
    return !mission.endedOn && !!last && last < toIsoDate(new Date())!;
  }
}

/**
 * The missions in progress on `today`: won, not archived, started by the end of its month (or without a start), and
 * not ended before the month began, so the month of its end still lists it. Earliest start first.
 */
export function activeMissions(projects: ProjectDto[], today: Date): ProjectDto[] {
  const monthStart = toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1))!;
  const monthEnd = toIsoDate(new Date(today.getFullYear(), today.getMonth() + 1, 0))!;
  return projects
    .filter(project => project.status === ProjectStatus.WON && !project.archivedAt &&
      (!project.startDate || project.startDate.substring(0, 10) <= monthEnd) &&
      (!project.endedOn || project.endedOn >= monthStart))
    .sort((a, b) => (a.startDate ?? '').localeCompare(b.startDate ?? ''));
}

function lastDayOf(mission: ProjectDto): string | undefined {
  if (mission.endedOn) {
    return mission.endedOn;
  }
  const start = fromIsoDate(mission.startDate);
  if (!start || !mission.durationInMonths) {
    return undefined;
  }
  return toIsoDate(new Date(start.getFullYear(), start.getMonth() + mission.durationInMonths, start.getDate() - 1));
}
