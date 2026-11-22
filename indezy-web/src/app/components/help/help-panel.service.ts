import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { HelpTopic } from './help-topics';

/** What the help panel opens on: an article, and the section to scroll to if any. */
export interface HelpPanelData {
  slug: string;
  section?: HelpTopic['section'];
}

/** Opens the help panel at the side, loaded on first use to keep it out of the first page's download. */
@Injectable({ providedIn: 'root' })
export class HelpPanelService {
  private readonly dialog = inject(MatDialog);

  /** The panel on the article explaining the page at {@code url}. */
  openForPage(url: string): Promise<void> {
    return import('./help-panel.component').then(({ helpArticleFor }) => this.open({ slug: helpArticleFor(url).slug }));
  }

  /** The panel on an article, scrolled to one of its sections when given. */
  open(data: HelpPanelData): Promise<void> {
    return import('./help-panel.component').then(({ HelpPanelComponent }) => {
      this.dialog.open(HelpPanelComponent, { data, position: { right: '0', top: '0' }, height: '100vh', width: '480px',
        maxWidth: '100vw', panelClass: 'help-panel-dialog', autoFocus: 'dialog' });
    });
  }
}
