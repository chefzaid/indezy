import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { NetworkFile } from '../../../models';
import { NetworkFilesComponent } from './network-files.component';
import en from '../../../../assets/i18n/en.json';

describe('NetworkFilesComponent', () => {
  let fixture: ComponentFixture<NetworkFilesComponent>;
  const image = (id: number): NetworkFile => ({ id, kind: 'IMAGE', name: `photo${id}.jpg`, contentType: 'image/jpeg', size: 2048, alt: `Photo ${id}`,
    width: 800, height: 600, pages: null, url: `/public/network-files/${id}/full?e=1&s=x`, previewUrl: null });
  const pdf: NetworkFile = { id: 9, kind: 'DOCUMENT', name: 'mission.pdf', contentType: 'application/pdf', size: 3 * 1024 * 1024, alt: null,
    width: null, height: null, pages: 12, url: '/public/network-files/9/full?e=1&s=x', previewUrl: '/public/network-files/9/preview?e=1&s=x' };
  const sheet: NetworkFile = { ...pdf, id: 10, name: 'rates', contentType: 'application/vnd.oasis.opendocument.spreadsheet', pages: null,
    previewUrl: null };

  function create(files: NetworkFile[] | null, compact = false): HTMLElement {
    TestBed.configureTestingModule({ imports: [NetworkFilesComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    fixture = TestBed.createComponent(NetworkFilesComponent);
    fixture.componentRef.setInput('files', files);
    fixture.componentRef.setInput('compact', compact);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('shows the images as a grid, each described and opening at full size, and the documents as cards', () => {
    const element = create([image(1), pdf, image(2), image(3), sheet]);
    const grid = element.querySelector('[data-files-images]')!;
    expect(grid.getAttribute('data-count')).toBe('3');
    const first = grid.querySelector('img')!;
    expect(first.alt).toBe('Photo 1');
    expect(first.getAttribute('loading')).toBe('lazy');
    expect((grid.querySelector('a') as HTMLAnchorElement).href).toMatch(/\/api\/public\/network-files\/1\/full/);
    const documents = element.querySelectorAll('[data-files-documents] li');
    expect(documents.length).toBe(2);
    expect(documents[0].textContent).toContain('mission.pdf');
    expect(documents[0].textContent).toContain('PDF · 3.0 MB · 12 pages');
    expect(documents[0].querySelector('img.page')).not.toBeNull();
    expect(documents[1].textContent).toContain('table_chart');
    expect(documents[1].textContent).toContain('download');
    expect(fixture.componentInstance.extension(sheet)).toBe('');
  });

  it('shows no first page in a message, and nothing without files', () => {
    let element = create([pdf], true);
    expect(element.querySelector('img.page')).toBeNull();
    expect(element.querySelector('[data-files-documents]')?.classList).toContain('compact');
    TestBed.resetTestingModule();
    element = create(null);
    expect(element.querySelector('[data-files-images]')).toBeNull();
    expect(element.querySelector('[data-files-documents]')).toBeNull();
  });
});
