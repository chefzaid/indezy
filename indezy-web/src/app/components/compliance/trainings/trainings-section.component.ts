import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, filter, switchMap } from 'rxjs';

import { Training, TrainingDetails } from '../../../models';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TrainingService } from '../../../services/training/training.service';
import { TrainingDialogComponent } from '../training-dialog/training-dialog.component';

/**
 * The Documents page's training record (see docs/features.md#training-record): each training with its dates, its
 * certification's validity and the funding asked for it, a request due soon pointed out.
 */
@Component({
  selector: 'app-trainings-section',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatCardModule, MatIconModule, MatMenuModule, TranslateModule],
  templateUrl: './trainings-section.component.html',
  styleUrls: ['./trainings-section.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TrainingsSectionComponent implements OnInit {
  private readonly trainingService = inject(TrainingService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  trainings: Training[] = [];

  ngOnInit(): void {
    this.trainingService.list().subscribe({
      next: trainings => this.show(trainings),
      error: () => this.notifications.error('trainings.loadError')
    });
  }

  add(): void {
    this.open(null).pipe(switchMap(details => this.trainingService.create(details))).subscribe({
      next: created => this.show([created, ...this.trainings]),
      error: () => this.notifications.error('trainings.saveError')
    });
  }

  edit(training: Training): void {
    this.open(training).pipe(switchMap(details => this.trainingService.update(training.id, details))).subscribe({
      next: saved => this.show(this.trainings.map(each => each.id === saved.id ? saved : each)),
      error: () => this.notifications.error('trainings.saveError')
    });
  }

  remove(training: Training): void {
    this.confirm.confirm({ titleKey: 'trainings.deleteTitle', messageKey: 'trainings.deleteMessage',
      messageParams: { title: training.title }, confirmKey: 'common.delete', danger: true }).pipe(
      filter(Boolean),
      switchMap(() => this.trainingService.delete(training.id))
    ).subscribe({
      next: () => this.show(this.trainings.filter(each => each.id !== training.id)),
      error: () => this.notifications.error('trainings.deleteError')
    });
  }

  private open(training: Training | null): Observable<TrainingDetails> {
    return this.dialog.open<TrainingDialogComponent, Training | null, TrainingDetails>(TrainingDialogComponent,
      { data: training, width: '560px', autoFocus: 'first-tabbable', maxWidth: '95vw' }).afterClosed().pipe(
      filter((details): details is TrainingDetails => !!details));
  }

  private show(trainings: Training[]): void {
    this.trainings = trainings;
    this.changes.markForCheck();
  }
}
