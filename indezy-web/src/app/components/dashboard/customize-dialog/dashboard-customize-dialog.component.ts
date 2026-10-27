import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { DashboardWidget } from '../../../models/user-management.models';

/** The usual order of the dashboard's blocks, every one shown. */
export const USUAL_LAYOUT: DashboardWidget[] = ['STATS', 'CHARTS', 'REMINDERS', 'OFFERS', 'ANALYTICS', 'RECENT']
  .map(id => ({ id: id as DashboardWidget['id'], visible: true }));

/** Shows, hides and reorders the dashboard's blocks: by dragging, or with the arrows from the keyboard. */
@Component({
  selector: 'app-dashboard-customize-dialog',
  imports: [DragDropModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatIconModule, TranslateModule],
  templateUrl: './dashboard-customize-dialog.component.html',
  styleUrls: ['./dashboard-customize-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class DashboardCustomizeDialogComponent {
  widgets: DashboardWidget[] = inject<DashboardWidget[]>(MAT_DIALOG_DATA).map(widget => ({ ...widget }));

  dropped(event: CdkDragDrop<DashboardWidget[]>): void {
    moveItemInArray(this.widgets, event.previousIndex, event.currentIndex);
  }

  move(index: number, by: -1 | 1): void {
    moveItemInArray(this.widgets, index, index + by);
  }

  toggle(index: number, visible: boolean): void {
    this.widgets[index] = { ...this.widgets[index], visible };
  }

  reset(): void {
    this.widgets = USUAL_LAYOUT.map(widget => ({ ...widget }));
  }
}
