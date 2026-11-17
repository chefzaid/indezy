import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';

import { ConfidentialMatch, DOCUMENT_ACCEPT, FileRef, IMAGE_ACCEPT, MAX_POST_DOCUMENTS, MAX_POST_IMAGES, NETWORK_FILE_MAX_BYTES,
  NetworkFile } from '../../../models';
import { NetworkFilesService } from '../../../services/network/network-files.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** A file being added: uploading, then ready with its description (for an image) and the workspace's names it holds. */
export interface PickedFile {
  key: number;
  name: string;
  image: boolean;
  /** The picture shown while it uploads and after, from the member's own copy. */
  local: string | null;
  file: NetworkFile | null;
  failed: boolean;
  alt: FormControl<string>;
  confidential: ConfidentialMatch[];
}

let nextKey = 0;

/**
 * Adds images (4 at most, each described for those who cannot see it) and documents (3 at most) to a post or a message,
 * each uploaded at once; a document naming the workspace's clients, ESNs or contacts says so before it goes.
 */
@Component({
  selector: 'app-file-picker',
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule, TranslateModule],
  templateUrl: './file-picker.component.html',
  styleUrl: './file-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class FilePickerComponent {
  private readonly api = inject(NetworkFilesService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  /** Smaller buttons, beside a message's field. */
  @Input() compact = false;
  /** Told whenever the files change, so that the form reads {@link valid} again. */
  @Output() readonly changed = new EventEmitter<void>();

  readonly imageAccept = IMAGE_ACCEPT;
  readonly documentAccept = DOCUMENT_ACCEPT;
  readonly size = NetworkFilesService.size;
  readonly icon = NetworkFilesService.icon;
  picked: PickedFile[] = [];

  constructor() {
    inject(DestroyRef).onDestroy(() => this.picked.forEach(item => this.revoke(item)));
  }

  get images(): PickedFile[] {
    return this.picked.filter(item => item.image);
  }

  get documents(): PickedFile[] {
    return this.picked.filter(item => !item.image);
  }

  /** Whether every file uploaded and every image is described. */
  get valid(): boolean {
    return this.picked.every(item => item.file !== null && (!item.image || item.alt.value.trim() !== ''));
  }

  get uploading(): boolean {
    return this.picked.some(item => item.file === null && !item.failed);
  }

  /** The files to attach, in their order. */
  refs(): FileRef[] {
    return this.picked.filter(item => item.file).map(item => item.image ? { id: item.file!.id, alt: item.alt.value.trim() } : { id: item.file!.id });
  }

  /** Uploads the files chosen, those beyond the limits or 50 MB left out with a word why. */
  pick(input: HTMLInputElement, image: boolean): void {
    const chosen = Array.from(input.files ?? []);
    input.value = '';
    const room = (image ? MAX_POST_IMAGES : MAX_POST_DOCUMENTS) - (image ? this.images : this.documents).length;
    if (chosen.length > room) {
      this.notifications.error(image ? 'network.files.tooManyImages' : 'network.files.tooManyDocuments');
    }
    for (const file of chosen.slice(0, Math.max(0, room))) {
      if (file.size > NETWORK_FILE_MAX_BYTES) {
        this.notifications.error('network.files.tooLarge');
        continue;
      }
      this.upload(file, image);
    }
  }

  /** Takes a file out, dropping it from the server when it uploaded. */
  remove(item: PickedFile): void {
    this.picked = this.picked.filter(other => other !== item);
    this.revoke(item);
    if (item.file) {
      this.api.discard(item.file.id).subscribe({ error: () => undefined });
    }
    this.changed.emit();
  }

  /** The workspace's names a document holds, as its warning lists them. */
  names(item: PickedFile): string {
    return item.confidential.map(match => match.found).join(', ');
  }

  /** Keeps a document that names the workspace's names, as the member chose. */
  keep(item: PickedFile): void {
    item.confidential = [];
  }

  /** Forgets the files once their post or message went out. */
  clear(): void {
    this.picked.forEach(item => this.revoke(item));
    this.picked = [];
    this.changed.emit();
  }

  private upload(file: File, image: boolean): void {
    const item: PickedFile = { key: nextKey++, name: file.name, image, local: image && typeof URL.createObjectURL === 'function'
      ? URL.createObjectURL(file) : null, file: null, failed: false, alt: new FormControl('', { nonNullable: true,
      validators: image ? [Validators.required, Validators.maxLength(300)] : [] }), confidential: [] };
    this.picked = [...this.picked, item];
    item.alt.valueChanges.subscribe(() => this.changed.emit());
    this.changed.emit();
    this.api.upload(file).subscribe({
      next: uploaded => {
        item.file = uploaded.file;
        item.confidential = uploaded.confidential;
        this.changed.emit();
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        item.failed = true;
        this.notifications.error(FilePickerComponent.reason(error, image));
        this.changed.emit();
        this.changes.markForCheck();
      }
    });
  }

  /** What to tell the member when a file is refused. */
  private static reason(error: HttpErrorResponse, image: boolean): string {
    if (error.status === 409) {
      return 'network.files.tooManyToday';
    }
    if (error.status === 503) {
      return 'network.files.scanUnavailable';
    }
    if (error.status === 400) {
      const message = typeof error.error?.message === 'string' ? error.error.message : '';
      // The server's wording for ClamAV's refusals (MalwareScanner.refusal); macros fall to documentRefused.
      if (message.includes('malware')) {
        return 'network.files.malware';
      }
      if (message.includes('password')) {
        return 'network.files.encrypted';
      }
      if (message.includes('too large or')) {
        return 'network.files.unscannable';
      }
      if (message.includes('safety check')) {
        return 'network.files.unsafe';
      }
      return image ? 'network.files.imageRefused' : 'network.files.documentRefused';
    }
    return 'network.files.uploadError';
  }

  private revoke(item: PickedFile): void {
    if (item.local && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(item.local);
    }
  }
}
