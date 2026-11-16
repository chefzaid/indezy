import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { NetworkFile, UploadedFile } from '../../models';

/** The images and documents of the network's posts and messages. */
@Injectable({
  providedIn: 'root'
})
export class NetworkFilesService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/network/files`;

  /** Uploads an image or a document, to attach to a post or a message. */
  upload(file: File): Observable<UploadedFile> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<UploadedFile>(this.API_URL, form);
  }

  /** Drops an uploaded file not attached yet. */
  discard(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  /** The full address of a file's signed address. */
  static address(path: string): string {
    return `${environment.apiUrl}${path}`;
  }

  /** A file size as people read it: 840 KB, 2.4 MB. */
  static size(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  /** The icon of a document by its type. */
  static icon(file: Pick<NetworkFile, 'contentType'>): string {
    const type = file.contentType;
    return type === 'application/pdf' ? 'picture_as_pdf' : type.includes('presentation') ? 'slideshow'
      : type.includes('sheet') ? 'table_chart' : 'description';
  }
}
