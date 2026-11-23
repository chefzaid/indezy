import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiProfileImport, Training, UserProfile } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { SkillNamesService } from '../../../services/skill/skill-names.service';
import { TrainingService } from '../../../services/training/training.service';
import { SkillNames } from '../../../shared/utils/skill-names';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { CV_IMPORT_SKILLS_MAX, CvImportData, ProfileCvImportDialogComponent } from './profile-cv-import-dialog.component';

describe('ProfileCvImportDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ProfileCvImportDialogComponent>>;
  let users: jasmine.SpyObj<UserManagementService>;
  let trainings: jasmine.SpyObj<TrainingService>;

  const reading: AiProfileImport = {
    firstName: 'Alice', lastName: 'Martin', phone: null, city: 'Lyon', position: 'Lead Java', company: null, bio: 'b'.repeat(600),
    website: null, linkedin: null, github: null, languages: ['Anglais', 'Klingon', 'Français'],
    skills: [{ name: 'Java', years: 10 }, { name: 'Kafka', years: 4 }, { name: 'Scrum', years: 0 }]
  };

  function create(current: Partial<UserProfile>): ProfileCvImportDialogComponent {
    ai = jasmine.createSpyObj('AiService', ['profileImport']);
    notifications = jasmine.createSpyObj('NotificationService', ['error', 'success']);
    trainings = jasmine.createSpyObj('TrainingService', ['list', 'create']);
    trainings.list.and.returnValue(of([]));
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    users = jasmine.createSpyObj('UserManagementService', ['linkedInImport']);
    const data: CvImportData = { current, languages: ['Français', 'Anglais'] };
    TestBed.configureTestingModule({
      imports: [ProfileCvImportDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: NotificationService, useValue: notifications }, { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data }, { provide: UserManagementService, useValue: users },
        { provide: TrainingService, useValue: trainings },
        { provide: SkillNamesService, useValue: { names$: of(new SkillNames({ JavaScript: ['JS'], Java: [] })) } }]
    });
    const fixture = TestBed.createComponent(ProfileCvImportDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function pick(component: ProfileCvImportDialogComponent, linkedIn = false): void {
    const input = document.createElement('input');
    input.type = 'file';
    const files = new DataTransfer();
    files.items.add(new File(['%PDF'], 'cv.pdf'));
    input.files = files.files;
    component.read({ target: input } as unknown as Event, linkedIn);
  }

  it('shows what the CV says beside the profile, ticked where the profile is empty, and fills in only what is ticked', () => {
    const component = create({ firstName: 'Alice', lastName: 'Dupont', city: '', skills: ['java'], skillDetails: { java: { level: 'EXPERT' } },
      languages: ['Français'] });
    ai.profileImport.and.returnValue(of(reading));
    pick(component);

    expect(ai.profileImport).toHaveBeenCalled();
    // The same first name is left out; a different last name is offered, unticked.
    expect(component.rows.map(row => [row.field, row.chosen])).toEqual([['lastName', false], ['city', true], ['position', true], ['bio', true]]);
    expect(component.rows[0].current).toBe('Dupont');
    expect(component.rows[3].found.length).toBe(500);
    // Skills already listed and languages the profile does not offer are left out.
    expect(component.skills.map(skill => skill.name)).toEqual(['Kafka', 'Scrum']);
    expect(component.skillsHere).toEqual(['java']);
    expect(component.overLimit).toBeFalse();
    expect(component.languages).toEqual(['Anglais']);
    expect(component.languagesLeftOut).toEqual(['Klingon']);

    component.rows[2].chosen = false;
    component.apply();
    expect(dialogRef.close).toHaveBeenCalledWith({
      city: 'Lyon', bio: 'b'.repeat(500), skills: ['java', 'Kafka', 'Scrum'],
      skillDetails: { java: { level: 'EXPERT' }, Kafka: { years: 4 } }, languages: ['Français', 'Anglais']
    });
  });

  it('adds skills only up to the profile limit and says when there is nothing new', () => {
    const full = Array.from({ length: CV_IMPORT_SKILLS_MAX - 1 }, (_, index) => `Skill ${index}`);
    const component = create({ firstName: 'Alice', lastName: 'Martin', city: 'Lyon', position: 'Lead Java', bio: 'b'.repeat(600), skills: full,
      languages: ['Anglais', 'Français'] });
    component.show(reading);
    expect(component.rows.map(row => row.field)).toEqual(['bio']);
    // One place left for three skills: the first is chosen, the member picks which.
    expect(component.overLimit).toBeTrue();
    expect(component.room).toBe(1);
    expect(component.skills.map(skill => [skill.name, skill.chosen])).toEqual([['Java', true], ['Kafka', false], ['Scrum', false]]);
    component.skills[0].chosen = false;
    component.skills[1].chosen = true;
    expect(component.chosenSkills).toBe(1);
    component.apply();
    expect(dialogRef.close).toHaveBeenCalledWith(jasmine.objectContaining({ skills: [...full, 'Kafka'] }));


    component.show({ ...reading, bio: 'b'.repeat(600), skills: [] });
    component.takeLanguages = false;
    component.rows[0].chosen = false;
    expect(component.anyChosen).toBeFalse();
  });

  it('says why a CV could not be read', () => {
    const component = create({});
    ai.profileImport.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Not a CV' } })));
    pick(component);
    expect(notifications.error).toHaveBeenCalledWith('profile.cvImport.unreadable');
    ai.profileImport.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } })));
    pick(component);
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.unavailable');
    expect(component.loading).toBeFalse();
    expect(component.reading).toBeNull();
    // No file chosen: nothing is read.
    component.read({ target: document.createElement('input') } as unknown as Event);
    expect(ai.profileImport).toHaveBeenCalledTimes(2);
  });

  it('reads LinkedIn\'s archive without AI, saying when the file is not one', () => {
    const component = create({ firstName: 'Alice' });
    users.linkedInImport.and.returnValue(of({ ...reading, bio: null }));
    pick(component, true);
    expect(users.linkedInImport).toHaveBeenCalled();
    expect(ai.profileImport).not.toHaveBeenCalled();
    expect(component.rows.map(row => row.field)).toContain('city');

    users.linkedInImport.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    pick(component, true);
    expect(notifications.error).toHaveBeenCalledWith('profile.cvImport.notLinkedIn');
    users.linkedInImport.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    pick(component, true);
    expect(notifications.error).toHaveBeenCalledWith('profile.cvImport.readError');
  });

  it('adds the certifications ticked to the training record, marking those it already names', () => {
    const component = create({ firstName: 'Alice' });
    trainings.list.and.returnValue(of([{ id: 1, title: 'AWS course', certification: 'aws solutions architect', fundingDue: false } as Training]));
    trainings.create.and.returnValue(of({ id: 2, title: 'PSM I', fundingDue: false } as Training));
    users.linkedInImport.and.returnValue(of({ ...reading, certifications: [
      { name: 'AWS Solutions Architect', authority: 'Amazon', obtainedOn: '2024-03-01', expiresOn: null },
      { name: ' PSM I ', authority: 'Scrum.org', obtainedOn: '2019-01-01', expiresOn: '2029-01-01' }, { name: ' ', authority: null, obtainedOn: null, expiresOn: null }
    ] }));
    pick(component, true);
    expect(component.certifications.map(certification => [certification.name, certification.here, certification.chosen]))
      .toEqual([['AWS Solutions Architect', true, false], ['PSM I', false, true]]);
    expect(component.newCertifications).toBe(1);

    component.rows.forEach(row => row.chosen = false);
    component.takeSkills = false;
    component.takeLanguages = false;
    expect(component.anyChosen).toBeTrue();
    component.apply();
    expect(trainings.create).toHaveBeenCalledOnceWith({ title: 'PSM I', provider: 'Scrum.org', certification: 'PSM I',
      certifiedOn: '2019-01-01', certificationExpiresOn: '2029-01-01' });
    expect(notifications.success).toHaveBeenCalledWith('profile.cvImport.certificationsAdded', undefined, { count: 1 });
    expect(dialogRef.close).toHaveBeenCalledWith({});
  });

  it('keeps the dialog open when the certifications cannot be added, and reads on when the trainings cannot be listed', () => {
    const component = create({});
    trainings.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    trainings.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    ai.profileImport.and.returnValue(of({ ...reading, certifications: [{ name: 'PSM I', authority: null, obtainedOn: null, expiresOn: null }] }));
    pick(component);
    expect(component.certifications[0].chosen).toBeTrue();
    component.apply();
    expect(notifications.error).toHaveBeenCalledWith('profile.cvImport.certificationsError');
    expect(component.saving).toBeFalse();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('takes no more skills into a full profile', () => {
    const component = create({ skills: Array.from({ length: CV_IMPORT_SKILLS_MAX }, (_, index) => `Skill ${index}`) });
    component.show(reading);
    expect(component.room).toBe(0);
    expect(component.skills).toEqual([]);
    expect(component.overLimit).toBeFalse();
  });

  it('folds the skills\' spellings and aliases into the profile\'s', () => {
    const component = create({ skills: ['JavaScript', 'spring-boot'] });
    component.show({ ...reading, skills: [{ name: 'JS', years: 3 }, { name: 'Spring Boot', years: 2 }, { name: 'java', years: 5 },
      { name: 'Java', years: 1 }] }, [], new SkillNames({ JavaScript: ['JS'], Java: [] }));
    expect(component.skillsHere).toEqual(['JavaScript', 'spring-boot']);
    expect(component.skills.map(skill => [skill.name, skill.years])).toEqual([['Java', 5]]);
  });
});
