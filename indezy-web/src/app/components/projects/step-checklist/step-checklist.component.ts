import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslateModule } from '@ngx-translate/core';

import { InterviewStepDto } from '../../../models';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * A step's preparation checklist on the project's timeline: how much is ready, and each item ticked at once and saved
 * with the step, put back if the save fails.
 */
@Component({
  selector: 'app-step-checklist',
  imports: [MatCheckboxModule, TranslateModule],
  templateUrl: './step-checklist.component.html',
  styleUrls: ['./step-checklist.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class StepChecklistComponent {
  @Input({ required: true }) step!: InterviewStepDto;
  /** The step as saved, with its checklist. */
  @Output() readonly saved = new EventEmitter<InterviewStepDto>();

  constructor(
    private readonly interviewStepService: InterviewStepService,
    private readonly notificationService: NotificationService
  ) {}

  get items(): { label: string; done: boolean }[] {
    return this.step.checklist ?? [];
  }

  get done(): number {
    return this.items.filter(item => item.done).length;
  }

  toggle(index: number): void {
    if (!this.step.id) {
      return;
    }
    const before = this.step;
    const checklist = this.items.map((item, i) => i === index ? { ...item, done: !item.done } : item);
    this.step = { ...before, checklist };
    this.interviewStepService.update(before.id!, { ...before, id: before.id!, checklist }).subscribe({
      next: saved => {
        this.step = saved;
        this.saved.emit(saved);
      },
      error: () => {
        this.step = before;
        this.notificationService.error('steps.checklist.saveError');
      }
    });
  }
}
