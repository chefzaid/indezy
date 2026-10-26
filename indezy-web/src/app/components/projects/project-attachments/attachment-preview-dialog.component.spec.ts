import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { AttachmentPreviewDialogComponent, AttachmentPreviewData } from './attachment-preview-dialog.component';

describe('AttachmentPreviewDialogComponent', () => {
  function create(data: AttachmentPreviewData): HTMLElement {
    TestBed.configureTestingModule({
      imports: [AttachmentPreviewDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }]
    });
    const fixture = TestBed.createComponent(AttachmentPreviewDialogComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows an image as an image and a PDF in a frame, from a local address freed on close', () => {
    const revoke = spyOn(URL, 'revokeObjectURL').and.callThrough();
    const image = create({ fileName: 'Badge.png', contentType: 'image/png', blob: new Blob(['png']) });
    expect(image.querySelector('img')!.getAttribute('src')).toMatch(/^blob:/);
    expect(image.querySelector('img')!.getAttribute('alt')).toBe('Badge.png');
    expect(image.querySelector('iframe')).toBeNull();

    TestBed.resetTestingModule();
    expect(revoke).toHaveBeenCalled();
    const pdf = create({ fileName: 'Brief.pdf', contentType: 'application/pdf', blob: new Blob(['%PDF']) });
    expect(pdf.querySelector('iframe')!.getAttribute('src')).toMatch(/^blob:/);
    expect(pdf.querySelector('h2')!.textContent).toContain('Brief.pdf');
  });
});
