import { Component, EventEmitter, Input, OnInit, Output, ChangeDetectionStrategy, LOCALE_ID, inject } from '@angular/core';
import { formatDate } from '@angular/common';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { ALL_SEASONS, SeasonSelection, SeasonService } from '../../../services/season/season.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { Season } from '../../../models/season.models';
import { SeasonDialogComponent, SeasonDialogData } from '../season-dialog/season-dialog.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { toIsoDate } from '../../../shared/locale/app-locale';

/**
 * Chooses which job-hunting season the dashboard shows ("all seasons" included) and manages
 * seasons: start a new one, edit, close or delete the selected one. The choice is remembered.
 */
@Component({
  selector: 'app-season-picker',
  imports: [
    MatSelectModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
    MatDialogModule,
    TranslateModule
  ],
  templateUrl: './season-picker.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./season-picker.component.scss']
})
export class SeasonPickerComponent implements OnInit {
  @Input({ required: true }) freelanceId!: number;
  /** The selected season, or null for every season. Emitted once seasons are loaded, then on change. */
  @Output() readonly selectionChange = new EventEmitter<Season | null>();

  readonly ALL = ALL_SEASONS;
  seasons: Season[] = [];
  selection: SeasonSelection = ALL_SEASONS;
  loaded = false;

  private readonly locale = inject(LOCALE_ID);

  constructor(
    private readonly seasonService: SeasonService,
    private readonly notificationService: NotificationService,
    private readonly dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.reload(SeasonService.readSavedSelection());
  }

  get selectedSeason(): Season | null {
    return this.seasons.find(season => season.id === this.selection) ?? null;
  }

  select(selection: SeasonSelection): void {
    this.selection = selection;
    SeasonService.saveSelection(selection);
    this.selectionChange.emit(this.selectedSeason);
  }

  openNewSeason(): void {
    this.openDialog({ freelanceId: this.freelanceId });
  }

  editSelected(): void {
    const season = this.selectedSeason;
    if (season) {
      this.openDialog({ freelanceId: this.freelanceId, season });
    }
  }

  /**
   * Ends the running season: it stops yesterday, so opportunities created from today on no longer
   * join it (a season that started today ends today). Its dashboard stays available.
   */
  closeSelected(): void {
    const season = this.selectedSeason;
    if (!season?.id) {
      return;
    }
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const lastDay = toIsoDate(yesterday)!;
    const endDate = lastDay < season.startDate ? season.startDate : lastDay;
    this.seasonService.update(season.id, { ...season, endDate }).subscribe({
      next: () => {
        this.notificationService.success('seasons.closedSuccess');
        this.reload(String(season.id));
      },
      error: () => this.notificationService.error('seasons.saveError')
    });
  }

  deleteSelected(): void {
    const season = this.selectedSeason;
    if (!season?.id) {
      return;
    }
    const data: ConfirmDialogData = {
      titleKey: 'seasons.deleteTitle',
      messageKey: 'seasons.deleteConfirm',
      messageParams: { name: season.name },
      confirmKey: 'common.delete',
      danger: true
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed().subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.seasonService.delete(season.id!).subscribe({
        next: () => {
          this.notificationService.success('seasons.deletedSuccess');
          this.reload(ALL_SEASONS);
        },
        error: () => this.notificationService.error('seasons.deleteError')
      });
    });
  }

  /** "12 Sep 2026 – now" style range for a season. */
  formatRange(season: Season): string {
    const start = formatDate(season.startDate, 'mediumDate', this.locale);
    return season.endDate ? `${start} – ${formatDate(season.endDate, 'mediumDate', this.locale)}` : start;
  }

  private openDialog(data: SeasonDialogData): void {
    this.dialog.open(SeasonDialogComponent, { data, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((saved?: Season) => {
        if (saved?.id) {
          this.reload(String(saved.id));
        }
      });
  }

  private reload(preferred: string | null): void {
    this.seasonService.getByFreelanceId(this.freelanceId).subscribe({
      next: seasons => {
        this.seasons = seasons;
        this.loaded = true;
        this.select(SeasonService.pickInitialSelection(seasons, preferred));
      },
      error: () => {
        this.seasons = [];
        this.loaded = true;
        this.notificationService.error('seasons.loadError');
        this.select(ALL_SEASONS);
      }
    });
  }
}
