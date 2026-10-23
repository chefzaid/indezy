import { ChangeDetectionStrategy, Component, LOCALE_ID, inject } from '@angular/core';
import { DecimalPipe, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { SavedOfferSearch, SavedSearchEmailFrequency, SavedSearchEmailsUpdate } from '../../../models/offer.models';
import { getSavedLanguage } from '../../../shared/locale/app-locale';

export interface SavedSearchEmailsDialogData {
  search: SavedOfferSearch;
}

/** The hour a daily email is sent at when none was chosen, Paris time. */
const DEFAULT_HOUR = 8;

/**
 * Asks how often a saved search should email its new offers: never, at once (grouped every 30 minutes) or once a day
 * at an hour, in the app's current language. Closes with the choice, or with nothing when cancelled.
 */
@Component({
  selector: 'app-saved-search-emails-dialog',
  imports: [DecimalPipe, FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatRadioModule, MatSelectModule, TranslateModule],
  templateUrl: './saved-search-emails-dialog.component.html',
  styleUrls: ['./saved-search-emails-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SavedSearchEmailsDialogComponent {
  private readonly dialogRef = inject<MatDialogRef<SavedSearchEmailsDialogComponent, SavedSearchEmailsUpdate>>(MatDialogRef);
  private readonly locale = inject(LOCALE_ID);
  readonly search = inject<SavedSearchEmailsDialogData>(MAT_DIALOG_DATA).search;
  readonly hours = Array.from({ length: 24 }, (_, hour) => hour);
  frequency: SavedSearchEmailFrequency = this.search.emails.frequency;
  hour = this.search.emails.hour ?? DEFAULT_HOUR;

  /** When sending stopped after 60 days of emails left unopened, while they are still off. */
  get stoppedOn(): string | null {
    const stopped = this.search.emails.stoppedAt;
    return stopped && this.search.emails.frequency === 'OFF' ? formatDate(stopped, 'longDate', this.locale) : null;
  }

  /** Emails can only be turned on when the server sends them; turning them off always works. */
  get canSave(): boolean {
    return this.search.emailsAvailable || this.frequency === 'OFF';
  }

  save(): void {
    if (!this.canSave) {
      return;
    }
    const language = getSavedLanguage() === 'en' ? 'EN' : 'FR';
    this.dialogRef.close(this.frequency === 'DAILY' ? { frequency: 'DAILY', hour: this.hour, language } : { frequency: this.frequency, language });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
