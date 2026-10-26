import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../assets/i18n/en.json';
import fr from '../../../assets/i18n/fr.json';
import { TranslatedPaginatorIntl } from './translated-paginator-intl';

describe('TranslatedPaginatorIntl', () => {
  let translate: TranslateService;
  let intl: TranslatedPaginatorIntl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [TranslatedPaginatorIntl]
    });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.setTranslation('fr', fr);
    translate.use('en');
    intl = TestBed.inject(TranslatedPaginatorIntl);
  });

  it('labels the paginator in the active language', () => {
    expect(intl.itemsPerPageLabel).toBe(en.pagination.itemsPerPage);
    expect(intl.nextPageLabel).toBe(en.pagination.nextPage);
    expect(intl.previousPageLabel).toBe(en.pagination.previousPage);
    expect(intl.firstPageLabel).toBe(en.pagination.firstPage);
    expect(intl.lastPageLabel).toBe(en.pagination.lastPage);
  });

  it('relabels the paginator when the language changes', () => {
    let changes = 0;
    intl.changes.subscribe(() => changes++);

    translate.use('fr');

    expect(intl.itemsPerPageLabel).toBe('Éléments par page');
    expect(intl.lastPageLabel).toBe('Dernière page');
    expect(intl.getRangeLabel(0, 10, 25)).toBe('1 – 10 sur 25');
    expect(changes).toBe(1);
  });

  it('describes the range of the current page', () => {
    translate.use('fr');

    expect(intl.getRangeLabel(2, 10, 25)).toBe('21 – 25 sur 25');
    expect(intl.getRangeLabel(0, 10, 0)).toBe('0 – 0 sur 0');
    expect(intl.getRangeLabel(0, 0, 25)).toBe('0 – 0 sur 25');
  });

  it('stops following language changes once destroyed', () => {
    intl.ngOnDestroy();

    translate.use('fr');

    expect(intl.itemsPerPageLabel).toBe(en.pagination.itemsPerPage);
  });
});
