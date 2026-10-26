import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectDto } from '../../../models';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * Archives an opportunity from its page, or, once archived, says since when and restores it to its pipeline column.
 * Both keep its status.
 */
@Component({
  selector: 'app-project-archive-action',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule],
  templateUrl: './project-archive-action.component.html',
  styleUrls: ['./project-archive-action.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProjectArchiveActionComponent {
  @Input({ required: true }) project!: ProjectDto;
  /** The opportunity as saved, archived or restored. */
  @Output() readonly changed = new EventEmitter<ProjectDto>();
  busy = false;

  constructor(
    private readonly projectService: ProjectService,
    private readonly notificationService: NotificationService
  ) {}

  toggle(): void {
    if (!this.project.id || this.busy) {
      return;
    }
    const archiving = !this.project.archivedAt;
    this.busy = true;
    (archiving ? this.projectService.archive(this.project.id) : this.projectService.restore(this.project.id)).subscribe({
      next: saved => {
        this.busy = false;
        this.notificationService.success(archiving ? 'archive.archivedSuccess' : 'archive.restoredSuccess');
        this.changed.emit(saved);
      },
      error: () => {
        this.busy = false;
        this.notificationService.error(archiving ? 'archive.archiveError' : 'archive.restoreError');
      }
    });
  }
}
