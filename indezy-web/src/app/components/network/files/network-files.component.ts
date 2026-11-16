import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkFile } from '../../../models';
import { NetworkFilesService } from '../../../services/network/network-files.service';

/**
 * The images and documents a post or a message carries: images as a grid (one wide, two side by side, three with one
 * tall, four in a square), each opening at full size; documents as cards with a PDF's first page, its name and size.
 */
@Component({
  selector: 'app-network-files',
  imports: [MatIconModule, TranslateModule],
  templateUrl: './network-files.component.html',
  styleUrl: './network-files.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NetworkFilesComponent {
  @Input({ required: true }) set files(files: NetworkFile[] | null | undefined) {
    this.images = (files ?? []).filter(file => file.kind === 'IMAGE');
    this.documents = (files ?? []).filter(file => file.kind === 'DOCUMENT');
  }
  /** Smaller, in a message's bubble. */
  @Input() compact = false;

  images: NetworkFile[] = [];
  documents: NetworkFile[] = [];

  readonly address = NetworkFilesService.address;
  readonly size = NetworkFilesService.size;
  readonly icon = NetworkFilesService.icon;

  /** The document's extension, in capitals, as its card shows it. */
  extension(file: NetworkFile): string {
    return file.name.includes('.') ? file.name.slice(file.name.lastIndexOf('.') + 1).toUpperCase() : '';
  }
}
