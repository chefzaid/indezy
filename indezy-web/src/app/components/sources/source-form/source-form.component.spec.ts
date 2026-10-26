import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { SourceDto } from '../../../models/source.models';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { SourceService } from '../../../services/source/source.service';
import { SourceFormComponent } from './source-form.component';

describe('SourceFormComponent', () => {
  let sources: jasmine.SpyObj<SourceService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let navigate: jasmine.Spy;

  const malt: SourceDto = { id: 4, name: 'Malt', type: 'JOB_BOARD', link: 'https://www.malt.fr', isListing: true,
    popularityRating: 5, usefulnessRating: 4, notes: 'Marketplace', freelanceId: 7 };

  function create(id?: number, load: Observable<SourceDto> = of(malt), userId = 7): ComponentFixture<SourceFormComponent> {
    sources = jasmine.createSpyObj('SourceService', ['getById', 'create', 'update']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    sources.getById.and.returnValue(load);
    TestBed.configureTestingModule({
      imports: [SourceFormComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { params: of(id ? { id } : {}) } },
        { provide: SourceService, useValue: sources },
        { provide: AuthService, useValue: { getUser: () => (userId ? { id: userId } : null) } },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(SourceFormComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<SourceFormComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  describe('creating a source', () => {
    it('creates the source for the signed-in freelancer and returns to the list', () => {
      const fixture = create();
      const component = fixture.componentInstance;
      sources.create.and.returnValue(of({ ...malt, id: 9 }));

      expect(component.isEditMode).toBeFalse();
      expect(component.pageTitle).toBe('sources.newSource');
      expect(component.submitButtonText).toBe('common.create');
      expect(text(fixture)).toContain('sources.newSubtitle');

      component.sourceForm.patchValue({ name: 'LinkedIn', type: 'SOCIAL_MEDIA', usefulnessRating: 3 });
      (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();

      expect(sources.create).toHaveBeenCalledWith(jasmine.objectContaining({
        name: 'LinkedIn', type: 'SOCIAL_MEDIA', link: '', isListing: false, usefulnessRating: 3, notes: '', freelanceId: 7
      }));
      expect(sources.create.calls.mostRecent().args[0].popularityRating).toBeNull();
      expect(notifications.success).toHaveBeenCalledWith('sources.createSuccess');
      expect(navigate).toHaveBeenCalledWith(['/sources']);
    });

    it('shows the errors of an invalid form instead of saving', () => {
      const component = create().componentInstance;
      component.sourceForm.patchValue({ name: 'L', popularityRating: 0, usefulnessRating: 6 });

      component.onSubmit();

      expect(sources.create).not.toHaveBeenCalled();
      expect(component.getFieldError('name')).toBe('errors.minLength');
      expect(component.getFieldError('popularityRating')).toBe('errors.minValue');
      expect(component.getFieldError('usefulnessRating')).toBe('errors.maxValue');
      expect(component.getFieldError('type')).toBe('');
    });

    it('refuses to save without a signed-in freelancer', () => {
      const component = create(undefined, of(malt), 0).componentInstance;
      component.sourceForm.patchValue({ name: 'LinkedIn' });

      component.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('errors.freelanceNotFound');
      expect(sources.create).not.toHaveBeenCalled();
      expect(component.isSubmitting).toBeFalse();
    });

    it('reports a failed creation', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      sources.create.and.returnValue(throwError(() => new Error('down')));
      component.sourceForm.patchValue({ name: 'LinkedIn' });

      component.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('errors.creatingSource');
      expect(component.isSubmitting).toBeFalse();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('cancels back to the list', () => {
      const fixture = create();

      (fixture.nativeElement.querySelector('button[type="button"]') as HTMLButtonElement).click();

      expect(navigate).toHaveBeenCalledWith(['/sources']);
    });
  });

  describe('editing a source', () => {
    it('loads the source and saves the changes', () => {
      const fixture = create(4);
      const component = fixture.componentInstance;
      sources.update.and.returnValue(of(malt));

      expect(sources.getById).toHaveBeenCalledWith(4);
      expect(component.isLoading).toBeFalse();
      expect(component.sourceForm.value).toEqual({
        name: 'Malt', type: 'JOB_BOARD', link: 'https://www.malt.fr', isListing: true, popularityRating: 5,
        usefulnessRating: 4, notes: 'Marketplace'
      });
      expect(component.pageTitle).toBe('sources.editSource');
      expect(component.submitButtonText).toBe('common.edit');
      expect(text(fixture)).toContain('sources.editSubtitle');

      component.sourceForm.patchValue({ notes: 'Best leads' });
      component.onSubmit();

      expect(sources.update).toHaveBeenCalledWith(4, jasmine.objectContaining({ name: 'Malt', notes: 'Best leads', freelanceId: 7 }));
      expect(notifications.success).toHaveBeenCalledWith('sources.updateSuccess');
      expect(navigate).toHaveBeenCalledWith(['/sources']);
    });

    it('reports a failed update', () => {
      spyOn(console, 'error');
      const component = create(4).componentInstance;
      sources.update.and.returnValue(throwError(() => new Error('down')));

      component.onSubmit();

      expect(notifications.error).toHaveBeenCalledWith('errors.updatingSource');
      expect(component.isSubmitting).toBeFalse();
    });

    it('returns to the list when the source cannot be loaded', () => {
      spyOn(console, 'error');
      const component = create(4, throwError(() => ({ status: 404 }))).componentInstance;

      expect(notifications.error).toHaveBeenCalledWith('errors.loadingSource');
      expect(navigate).toHaveBeenCalledWith(['/sources']);
      expect(component.isLoading).toBeFalse();
    });
  });
});
