import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

/** The records picked in a list (clients, contacts): how many, and deleting them at once. */
@Component({
  selector: 'app-list-selection-bar',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  templateUrl: './list-selection-bar.component.html',
  styleUrls: ['../../../components/project-selection-bar/project-selection-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ListSelectionBarComponent {
  @Input({ required: true }) count = 0;
  @Input() busy = false;
  @Output() readonly delete = new EventEmitter<void>();
  @Output() readonly clear = new EventEmitter<void>();
}
