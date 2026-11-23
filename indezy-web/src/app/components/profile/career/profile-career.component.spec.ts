import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { Education, Experience } from '../../../models';
import { CareerService } from '../../../services/career/career.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ProfileCareerComponent } from './profile-career.component';

describe('ProfileCareerComponent', () => {
  let career: jasmine.SpyObj<CareerService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const bank: Experience = { id: 1, employer: 'Banque', title: 'Dev Java', startsOn: '2015-09-01', endsOn: null, stack: 'Java',
    shownPublicly: true };
  const insa: Education = { id: 2, school: 'INSA Lyon', degree: 'Ingénieure', startYear: 2010, endYear: 2015, shownPublicly: false };

  function create(): { component: ProfileCareerComponent; element: HTMLElement } {
    career = jasmine.createSpyObj('CareerService', ['experiences', 'educations', 'createExperience', 'updateExperience', 'deleteExperience',
      'createEducation', 'updateEducation', 'deleteEducation']);
    Object.assign(career, { changed: new Subject<void>() });
    career.experiences.and.returnValue(of([bank]));
    career.educations.and.returnValue(of([insa]));
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [ProfileCareerComponent, TranslateModule.forRoot()],
      providers: [{ provide: CareerService, useValue: career }, { provide: MatDialog, useValue: dialog },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(ProfileCareerComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement };
  }

  function closing(value: unknown): void {
    dialog.open.and.returnValue({ afterClosed: () => of(value) } as ReturnType<MatDialog['open']>);
  }

  it('lists the past positions and schools', () => {
    const { component, element } = create();
    expect(element.querySelector('[data-experience="1"]')?.textContent).toContain('Dev Java · Banque');
    expect(element.querySelector('[data-education="2"]')?.textContent).toContain('Ingénieure · INSA Lyon');
    expect(component.years(insa)).toBe('2010 – 2015');
    expect(component.years({ ...insa, startYear: null })).toBe('2015');
  });

  it('adds, changes and deletes a position, reading the lists again', () => {
    const { component } = create();
    const details = { employer: 'Autre', title: 'Lead', shownPublicly: false };
    closing(details);
    career.createExperience.and.returnValue(of({ ...details, id: 5 }));
    component.addExperience();
    expect(career.createExperience).toHaveBeenCalledWith(details);
    expect(career.experiences).toHaveBeenCalledTimes(2);

    career.updateExperience.and.returnValue(of(bank));
    component.editExperience(bank);
    expect(career.updateExperience).toHaveBeenCalledWith(1, details);

    confirm.confirm.and.returnValue(of(true));
    career.deleteExperience.and.returnValue(of(undefined));
    component.removeExperience(bank);
    expect(component.experiences).toEqual([]);

    // A closed dialog saves nothing.
    closing(undefined);
    component.addExperience();
    expect(career.createExperience).toHaveBeenCalledTimes(1);
  });

  it('adds, changes and deletes a school, saying when it fails', () => {
    const { component } = create();
    const details = { school: 'Lycée', shownPublicly: true };
    closing(details);
    career.createEducation.and.returnValue(throwError(() => new Error('down')));
    component.addEducation();
    expect(notifications.error).toHaveBeenCalledWith('career.saveError');

    career.updateEducation.and.returnValue(of(insa));
    component.editEducation(insa);
    expect(career.updateEducation).toHaveBeenCalledWith(2, details);

    confirm.confirm.and.returnValue(of(true));
    career.deleteEducation.and.returnValue(throwError(() => new Error('down')));
    component.removeEducation(insa);
    expect(notifications.error).toHaveBeenCalledWith('career.deleteError');
    career.deleteEducation.and.returnValue(of(undefined));
    component.removeEducation(insa);
    expect(component.educations).toEqual([]);

    career.experiences.and.returnValue(throwError(() => new Error('down')));
    career.changed.next();
    expect(notifications.error).toHaveBeenCalledWith('career.loadError');
  });
});
