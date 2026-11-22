import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { HelpPanelService } from './help-panel.service';
import { HELP_TOPICS, HelpTopicKey } from './help-topics';

/**
 * A small help link beside a setting that needs explaining (see docs/features.md#help-center-and-legal-pages): it opens
 * the help panel at the section that explains it.
 */
@Component({
  selector: 'app-help-link',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule],
  template: `
    <button mat-icon-button type="button" class="help-link" (click)="open($event)" [attr.data-help-link]="topic"
      [aria-label]="'help.link.' + topic | translate" [matTooltip]="'help.link.' + topic | translate">
      <mat-icon>help_outline</mat-icon>
    </button>
  `,
  styles: [`
    :host { display: inline-flex; vertical-align: middle; }
    .help-link { --mat-icon-button-state-layer-size: 32px; width: 32px; height: 32px; padding: 4px; }
    .help-link mat-icon { font-size: 20px; width: 20px; height: 20px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HelpLinkComponent {
  @Input({ required: true }) topic!: HelpTopicKey;
  private readonly panel = inject(HelpPanelService);

  open(event: Event): void {
    // Inside a form or a card header, the link only opens the help.
    event.preventDefault();
    event.stopPropagation();
    const topic = HELP_TOPICS[this.topic];
    void this.panel.open({ slug: topic.article, section: topic.section });
  }
}
