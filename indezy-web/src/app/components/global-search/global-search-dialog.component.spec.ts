import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { GlobalSearchResult } from '../../models/search.models';
import { GlobalSearchService } from '../../services/search/global-search.service';
import { GlobalSearchDialogComponent } from './global-search-dialog.component';

describe('GlobalSearchDialogComponent', () => {
  let fixture: ComponentFixture<GlobalSearchDialogComponent>;
  let component: GlobalSearchDialogComponent;
  let search: jasmine.SpyObj<GlobalSearchService>;
  let close: jasmine.Spy;
  let navigate: jasmine.Spy;

  const found: GlobalSearchResult = {
    projects: [{ id: 4, title: 'Développeur Kafka', subtitle: 'Société Générale', excerpt: 'Flux Kafka temps réel' }],
    clients: [],
    contacts: [{ id: 7, title: 'Hélène Durand', subtitle: 'Société Générale' }],
    notes: [{ id: 9, title: 'Développeur Kafka', excerpt: 'Appel avec Hélène', projectId: 4 }]
  };

  function create(): void {
    search = jasmine.createSpyObj('GlobalSearchService', ['search']);
    search.search.and.returnValue(of(found));
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [GlobalSearchDialogComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: GlobalSearchService, useValue: search },
        { provide: MatDialogRef, useValue: { close } }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(GlobalSearchDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function type(value: string): void {
    component.query.setValue(value);
    tick(200);
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function key(name: string): void {
    element().querySelector<HTMLInputElement>('.search-input')!.dispatchEvent(new KeyboardEvent('keydown', { key: name }));
    fixture.detectChanges();
  }

  it('waits for two characters, then lists the hits by kind with the words highlighted', fakeAsync(() => {
    create();
    expect(element().textContent).toContain('globalSearch.hint');

    type(' k ');
    expect(search.search).not.toHaveBeenCalled();

    type(' kafka ');
    expect(search.search).toHaveBeenCalledOnceWith('kafka');
    expect(Array.from(element().querySelectorAll('.search-group h3')).map(h => h.textContent!.trim()))
      .toEqual(['globalSearch.kinds.projects', 'globalSearch.kinds.contacts', 'globalSearch.kinds.notes']);
    expect(Array.from(element().querySelectorAll('mark')).map(mark => mark.textContent)).toEqual(['Kafka', 'Kafka', 'Kafka']);
    expect(element().querySelector('[role="option"].active')!.id).toBe('global-search-option-0');
    expect(element().querySelector('.search-input')!.getAttribute('aria-activedescendant')).toBe('global-search-option-0');
  }));

  it('moves through the hits with the arrow keys, round, and opens one with Enter', fakeAsync(() => {
    create();
    type('helene');

    key('ArrowUp');
    expect(component.active).toBe(2);
    key('ArrowDown');
    key('ArrowDown');
    expect(component.active).toBe(1);
    key('Enter');

    expect(close).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/contacts', 7]);
  }));

  it('opens a note on its opportunity, and any hit on a click', fakeAsync(() => {
    create();
    type('appel');
    element().querySelectorAll<HTMLElement>('[role="option"]')[2].click();
    expect(navigate).toHaveBeenCalledWith(['/projects', 4]);

    component.open(component.options[0]);
    expect(navigate).toHaveBeenCalledWith(['/projects', 4]);
    element().querySelectorAll<HTMLElement>('[role="option"]')[1].dispatchEvent(new Event('mouseenter'));
    expect(component.active).toBe(1);
  }));

  it('says when nothing was found or the search failed, and ignores keys without hits', fakeAsync(() => {
    create();
    search.search.and.returnValues(of({ projects: [], clients: [], contacts: [], notes: [] }), throwError(() => new Error('down')));

    type('zzz');
    expect(element().textContent).toContain('globalSearch.empty');
    key('Enter');
    expect(navigate).not.toHaveBeenCalled();

    type('yyy');
    expect(element().textContent).toContain('globalSearch.error');
    expect(component.loading).toBe(false);
  }));

  it('keeps a note without its opportunity on its own id', () => {
    create();
    const link = component.kinds.find(kind => kind.kind === 'notes')!.link({ id: 3, title: 'x' });
    expect(link).toEqual(['/projects', 3]);
  });
});
