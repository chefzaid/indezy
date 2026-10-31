import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { CONTRACT_POINTS, ContractCheck, ContractPoint, ProjectDto } from '../../../../models/project.models';
import { NotificationService } from '../../../../services/notification/notification.service';
import { ProjectService } from '../../../../services/project/project.service';

/** A won mission's contract checklist: each point ticked once checked in the contract, with what the contract says. */
@Component({
  selector: 'app-contract-checklist',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [FormsModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule],
  templateUrl: './contract-checklist.component.html',
  styleUrl: './contract-checklist.component.scss'
})
export class ContractChecklistComponent implements OnChanges {
  private readonly projectService = inject(ProjectService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) project!: ProjectDto;

  checks: ContractCheck[] = [];

  ngOnChanges(): void {
    const saved = new Map((this.project.contractChecks ?? []).map(check => [check.point, check] as const));
    this.checks = CONTRACT_POINTS.map(point => ({ point, checked: saved.get(point)?.checked ?? false, note: saved.get(point)?.note ?? '' }));
  }

  get checkedCount(): number {
    return this.checks.filter(check => check.checked).length;
  }

  toggle(point: ContractPoint, checked: boolean): void {
    this.checks = this.checks.map(check => check.point === point ? { ...check, checked } : check);
    this.save();
  }

  /** Saves a note once it is left, if it changed. */
  saveNote(point: ContractPoint, note: string): void {
    const saved = (this.project.contractChecks ?? []).find(check => check.point === point)?.note ?? '';
    if (note.trim() === saved.trim()) {
      return;
    }
    this.checks = this.checks.map(check => check.point === point ? { ...check, note } : check);
    this.save();
  }

  private save(): void {
    const checks = this.checks.filter(check => check.checked || check.note?.trim())
      .map(check => ({ point: check.point, checked: check.checked, note: check.note?.trim() || null }));
    this.projectService.replaceContractChecks(this.project.id!, checks).subscribe({
      next: project => {
        this.project = { ...this.project, contractChecks: project.contractChecks ?? [] };
        this.changes.markForCheck();
      },
      error: () => {
        this.notifications.error('projects.contract.saveError');
        this.ngOnChanges();
        this.changes.markForCheck();
      }
    });
  }
}
