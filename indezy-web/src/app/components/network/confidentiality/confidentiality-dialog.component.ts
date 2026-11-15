import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ConfidentialMatch } from '../../../models';

/** The text about to go out and the workspace's names found in it. */
export interface ConfidentialityData {
  text: string;
  matches: ConfidentialMatch[];
}

/** What the member chose: publish the text as it is, or go back to it anonymized. */
export type ConfidentialityChoice = { action: 'publish'; text: string } | { action: 'edit'; text: string };

/** Names the clients, ESNs and contacts found in a text, to anonymize or to publish anyway. */
@Component({
  selector: 'app-confidentiality-dialog',
  imports: [MatDialogModule, MatButtonModule, TranslateModule],
  templateUrl: './confidentiality-dialog.component.html',
  styles: [`.found { margin: 0 0 12px; padding-left: 20px; } .found li { margin: 4px 0; } .name { font-weight: 600; }
    .kind { color: var(--text-secondary); }`],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ConfidentialityDialogComponent {
  private readonly translate = inject(TranslateService);
  private readonly dialogRef = inject<MatDialogRef<ConfidentialityDialogComponent, ConfidentialityChoice>>(MatDialogRef);
  readonly data = inject<ConfidentialityData>(MAT_DIALOG_DATA);

  /** What the name becomes once anonymized: "a client in Paris", "a consultancy", "a contact". */
  replacement(match: ConfidentialMatch): string {
    if (match.kind === 'CLIENT' && match.city) {
      return this.translate.instant('network.confidentiality.replacements.CLIENT_CITY', { city: match.city });
    }
    return this.translate.instant('network.confidentiality.replacements.' + match.kind);
  }

  /** The text with every name found replaced. */
  anonymized(): string {
    return this.data.matches.reduce((text, match) => text.split(match.found).join(this.replacement(match)), this.data.text);
  }

  anonymize(): void {
    this.dialogRef.close({ action: 'edit', text: this.anonymized() });
  }

  publishAnyway(): void {
    this.dialogRef.close({ action: 'publish', text: this.data.text });
  }
}
