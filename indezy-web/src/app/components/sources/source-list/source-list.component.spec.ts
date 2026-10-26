import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import en from '../../../../assets/i18n/en.json';
import { SourceDto, SourceType } from '../../../models/source.models';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { SourceService } from '../../../services/source/source.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { SourceListComponent } from './source-list.component';

describe('SourceListComponent', () => {
  let sources: jasmine.SpyObj<SourceService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let navigate: jasmine.Spy;

  const malt: SourceDto = { id: 1, name: 'Malt', type: 'JOB_BOARD', link: 'https://www.malt.fr', popularityRating: 5,
    usefulnessRating: 4, notes: 'Freelance marketplace', freelanceId: 7 };
  const linkedin: SourceDto = { id: 2, name: 'LinkedIn', type: 'SOCIAL_MEDIA', usefulnessRating: 3, freelanceId: 7 };
  const phone: SourceDto = { id: 3, name: 'Recruiter calls', type: 'CALL', freelanceId: 7 };

  function create(load: Observable<SourceDto[]> = of([malt, linkedin, phone]), userId = 7): ComponentFixture<SourceListComponent> {
    sources = jasmine.createSpyObj('SourceService', ['getByFreelanceId', 'delete', 'getSourceTypes']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    sources.getByFreelanceId.and.returnValue(load);
    sources.getSourceTypes.and.returnValue(['JOB_BOARD', 'SOCIAL_MEDIA', 'EMAIL', 'CALL', 'SMS']);
    TestBed.configureTestingModule({
      imports: [SourceListComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: SourceService, useValue: sources },
        { provide: AuthService, useValue: { getUser: () => (userId ? { id: userId } : null) } },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(SourceListComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<SourceListComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function names(component: SourceListComponent): string[] {
    return component.filteredSources.map(source => source.name);
  }

  it('lists the freelancer\'s sources with their type, link and ratings', () => {
    const fixture = create();

    expect(sources.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.isLoading).toBeFalse();
    const page = text(fixture);
    expect(page).toContain('3 source(s)');
    expect(page).toContain('Malt');
    expect(page).toContain('Job board');
    expect(page).toContain('Social media');
    expect(page).toContain('★★★★★');
    expect(page).toContain('★★★★☆');
    expect(fixture.nativeElement.querySelector('a[href="https://www.malt.fr"]')).not.toBeNull();
  });

  it('loads nothing when nobody is signed in', () => {
    const component = create(undefined, 0).componentInstance;

    expect(sources.getByFreelanceId).not.toHaveBeenCalled();
    expect(component.isLoading).toBeFalse();
  });

  it('reports sources that cannot be loaded', () => {
    const component = create(throwError(() => new Error('down'))).componentInstance;

    expect(notifications.error).toHaveBeenCalledWith('errors.loadingSources');
    expect(component.isLoading).toBeFalse();
  });

  it('invites to add a first source', () => {
    const fixture = create(of([]));

    expect(text(fixture)).toContain(en.sources.noSources);
    const add = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(button => button.textContent!.includes(en.sources.addSource))!;
    add.click();
    expect(navigate).toHaveBeenCalledWith(['/sources/new']);
  });

  it('filters by text, type and minimum usefulness, then clears the filters', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    component.searchQuery = ' MARKETPLACE ';
    component.applyFilters();
    expect(names(component)).toEqual(['Malt']);
    component.searchQuery = 'linkedin';
    component.applyFilters();
    expect(names(component)).toEqual(['LinkedIn']);

    component.searchQuery = '';
    component.typeFilter = 'CALL';
    component.applyFilters();
    expect(names(component)).toEqual(['Recruiter calls']);

    component.typeFilter = '';
    component.minRating = 4;
    component.applyFilters();
    expect(names(component)).toEqual(['Malt']);

    component.minRating = 5;
    component.applyFilters();
    expect(text(fixture)).toContain(en.common.noResults);

    const clear = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(button => button.textContent!.includes(en.common.clearFilters))!;
    clear.click();
    expect(names(component).length).toBe(3);
    expect(component.minRating).toBe(0);
  });

  it('opens the forms to create and edit a source', () => {
    const component = create().componentInstance;

    component.onCreate();
    expect(navigate).toHaveBeenCalledWith(['/sources/new']);
    component.onEdit(malt);
    expect(navigate).toHaveBeenCalledWith(['/sources', 1, 'edit']);
  });

  it('labels each type with an icon and shows ratings as stars', () => {
    const component = create().componentInstance;
    const types: SourceType[] = ['JOB_BOARD', 'SOCIAL_MEDIA', 'EMAIL', 'CALL', 'SMS'];

    expect(types.map(type => component.getTypeIcon(type))).toEqual(['work', 'share', 'email', 'phone', 'sms']);
    expect(component.getTypeIcon('OTHER' as SourceType)).toBe('source');
    expect(component.getTypeLabel('CALL')).toBe(en.sources.types.CALL);
    expect(component.getRatingDisplay(undefined)).toBe('-');
    expect(component.getRatingDisplay(2)).toBe('★★☆☆☆');
  });

  describe('deleting a source', () => {
    it('refuses a source without id and says so', () => {
      const component = create().componentInstance;

      component.onDelete({ ...malt, id: undefined });

      expect(confirm.confirm).not.toHaveBeenCalled();
      expect(notifications.error).toHaveBeenCalledWith('errors.missingSourceId');
    });

    it('keeps the source when the deletion is not confirmed', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(false));

      component.onDelete(malt);

      expect(confirm.confirm).toHaveBeenCalledWith({
        messageKey: 'sources.confirmDelete', messageParams: { name: 'Malt' }, danger: true
      });
      expect(sources.delete).not.toHaveBeenCalled();
    });

    it('deletes the source and reloads the list', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      sources.delete.and.returnValue(of(undefined));
      sources.getByFreelanceId.and.returnValue(of([linkedin]));

      component.onDelete(malt);

      expect(sources.delete).toHaveBeenCalledWith(1);
      expect(notifications.success).toHaveBeenCalledWith('sources.deleteSuccess', 2000);
      expect(names(component)).toEqual(['LinkedIn']);
    });

    it('reports a failed deletion', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      sources.delete.and.returnValue(throwError(() => new Error('down')));

      component.onDelete(malt);

      expect(notifications.error).toHaveBeenCalledWith('errors.deletingSource');
    });
  });
});
