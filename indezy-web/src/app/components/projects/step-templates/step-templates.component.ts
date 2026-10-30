import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { PipelineTemplate, ProjectDto } from '../../../models';
import { PipelineTemplateService } from '../../../services/pipeline-template/pipeline-template.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { TemplateSaveDialogComponent } from '../template-save-dialog/template-save-dialog.component';

/**
 * The steps card's templates menu: applies a saved recruitment process to the opportunity (its client's or ESN's
 * first), saves its steps as one, or deletes one.
 */
@Component({
  selector: 'app-step-templates',
  imports: [MatButtonModule, MatDividerModule, MatIconModule, MatMenuModule, TranslateModule],
  templateUrl: './step-templates.component.html',
  styleUrls: ['./step-templates.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class StepTemplatesComponent {
  @Input({ required: true }) project!: ProjectDto;
  /** Whether the opportunity has steps to save. */
  @Input() hasSteps = false;
  /** Steps were added from a template. */
  @Output() readonly applied = new EventEmitter<void>();

  templates: PipelineTemplate[] = [];
  loaded = false;

  constructor(
    private readonly templateService: PipelineTemplateService,
    private readonly notificationService: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly dialog: MatDialog,
    private readonly translate: TranslateService
  ) {}

  /** Reads the templates each time the menu opens, the opportunity's client's and ESN's first. */
  load(): void {
    this.templateService.list().subscribe({
      next: templates => {
        const own = new Set<number | undefined>([this.project.clientId, this.project.middlemanId].filter(id => id !== undefined));
        this.templates = [...templates].sort((a, b) =>
          Number(!own.has(a.clientId)) - Number(!own.has(b.clientId)) || a.name.localeCompare(b.name));
        this.loaded = true;
      },
      error: () => this.notificationService.error('steps.templates.loadError')
    });
  }

  apply(template: PipelineTemplate): void {
    this.templateService.apply(template.id, this.project.id!).subscribe({
      next: steps => {
        this.notificationService.successText(
          this.translate.instant('steps.templates.applied', { count: steps.length, name: template.name }), 3000);
        this.applied.emit();
      },
      error: () => this.notificationService.error('steps.templates.applyError')
    });
  }

  save(): void {
    this.dialog.open(TemplateSaveDialogComponent, { data: this.project, autoFocus: 'first-tabbable' });
  }

  remove(template: PipelineTemplate, event: Event): void {
    event.stopPropagation();
    this.confirmDialog.confirm({
      messageKey: 'steps.templates.deleteConfirm', messageParams: { name: template.name }, danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.templateService.delete(template.id).subscribe({
        next: () => {
          this.templates = this.templates.filter(item => item.id !== template.id);
          this.notificationService.success('steps.templates.deleted');
        },
        error: () => this.notificationService.error('steps.templates.deleteError')
      });
    });
  }
}
