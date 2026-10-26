import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { BehaviorSubject, filter } from 'rxjs';
import { AuthService } from '../../services/auth/auth.service';
import { ShortcutHelpComponent } from './shortcut-help.component';
import { GlobalSearchDialogComponent } from '../../components/global-search/global-search-dialog.component';

/**
 * Open dialogs, menus and selection panels, which Material renders in the CDK overlay. A list of chips or options shown
 * in the page itself (a filter's `mat-chip-listbox`) is not one.
 */
const OPEN_PANELS = ['dialog', 'menu', 'listbox'].map(role => `.cdk-overlay-container [role="${role}"]`).join(', ');

@Injectable({ providedIn: 'root' })
export class KeyboardShortcutsService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly searchRequests = new BehaviorSubject(false);
  readonly focusProjectSearch$ = this.searchRequests.pipe(filter(Boolean));

  acknowledgeSearchFocus(): void { this.searchRequests.next(false); }

  handle(event: KeyboardEvent): void {
    if (!event.altKey || !event.shiftKey || event.ctrlKey || event.metaKey || event.repeat
        || event.isComposing || event.defaultPrevented || !this.auth.isAuthenticated()) { return; }
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"]), [role="textbox"], [role="combobox"]')
        || this.document.querySelector(OPEN_PANELS)) { return; }

    switch (event.code) {
      case 'KeyN':
        event.preventDefault();
        void this.router.navigate(['/projects/new']);
        break;
      case 'KeyF':
        event.preventDefault();
        void this.router.navigate(['/projects']).then(() => this.searchRequests.next(true));
        break;
      case 'KeyM': {
        const button = this.document.querySelector<HTMLButtonElement>('[data-project-status]');
        const projectId = target?.closest('[data-project-id]')?.getAttribute('data-project-id');
        if (button && !button.disabled) {
          event.preventDefault();
          button.focus();
          button.click();
        } else if (projectId && /^[1-9]\d*$/.test(projectId)) {
          event.preventDefault();
          void this.router.navigate(['/projects', projectId], { queryParams: { focus: 'status' } });
        }
        break;
      }
      case 'KeyS':
        event.preventDefault();
        this.openSearch();
        break;
      case 'KeyH':
        event.preventDefault();
        this.openHelp();
        break;
    }
  }

  /** The search through the whole workspace, at the top of the screen. */
  openSearch(): void {
    this.dialog.open(GlobalSearchDialogComponent, {
      width: '640px', maxWidth: '95vw', position: { top: '10vh' }, autoFocus: 'first-tabbable', panelClass: 'global-search-panel'
    });
  }

  openHelp(): void {
    this.dialog.open(ShortcutHelpComponent, { width: '560px', maxWidth: '95vw' });
  }
}
