import { Component } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-shortcut-help',
  imports: [MatDialogModule, MatButtonModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'shortcuts.title' | translate }}</h2>
    <mat-dialog-content>
      <dl>
        @for (entry of entries; track entry.key) {
          <div><dt><kbd>Alt + Shift + {{ entry.key }}</kbd></dt><dd>{{ entry.label | translate }}</dd></div>
        }
      </dl>
      <p>{{ 'shortcuts.typingHint' | translate }}</p>
      <h3>{{ 'shortcuts.feedTitle' | translate }}</h3>
      <dl data-feed-shortcuts>
        @for (entry of feedEntries; track entry.label) {
          <div><dt><kbd>{{ entry.keys }}</kbd></dt><dd>{{ entry.label | translate }}</dd></div>
        }
      </dl>
      <p>{{ 'shortcuts.feedHint' | translate }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    dl { margin: 0; }
    dl div { display: flex; flex-wrap: wrap; gap: 8px 20px; padding: 12px 0; }
    dt { min-width: 140px; }
    dd { flex: 1; min-width: 180px; margin: 0; }
    kbd { font-size: .9rem; }
    p { color: var(--app-text-muted); margin-top: 20px; }
    h3 { margin: 24px 0 0; font-size: 1rem; }
  `
})
export class ShortcutHelpComponent {
  readonly entries = [
    { key: 'N', label: 'shortcuts.newProject' },
    { key: 'F', label: 'shortcuts.search' },
    { key: 'M', label: 'shortcuts.move' },
    { key: 'H', label: 'shortcuts.help' }
  ];

  /** Single keys of the offer feed, active while one of its offers has the focus. */
  readonly feedEntries = [
    { keys: 'J / K', label: 'shortcuts.feed.move' },
    { keys: 'O', label: 'shortcuts.feed.open' },
    { keys: 'S', label: 'shortcuts.feed.save' },
    { keys: 'H', label: 'shortcuts.feed.hide' },
    { keys: 'A', label: 'shortcuts.feed.add' },
    { keys: 'D', label: 'shortcuts.feed.details' },
    { keys: 'N', label: 'shortcuts.feed.note' },
    { keys: 'X', label: 'shortcuts.feed.select' }
  ];
}
