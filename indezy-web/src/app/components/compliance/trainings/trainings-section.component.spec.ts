import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { Training } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { TrainingService } from '../../../services/training/training.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { TrainingDialogComponent } from '../training-dialog/training-dialog.component';
import { TrainingsSectionComponent } from './trainings-section.component';

describe('TrainingsSectionComponent', () => {
  const cka: Training = { id: 1, title: 'CKA', provider: 'Linux Foundation', startsOn: '2026-11-16', endsOn: '2026-11-18', hours: 21,
    cost: 1800, certification: 'CKA', certificationExpiresOn: '2028-11-18', certificationValidity: 'VALID', fund: 'FIF_PL',
    fundingStatus: 'TO_REQUEST', fundingDeadline: '2026-11-20', fundingDue: true, fundedAmount: 900 };
  let fixture: ComponentFixture<TrainingsSectionComponent>;
  let trainings: jasmine.SpyObj<TrainingService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(list: Training[] | Error = [cka]): HTMLElement {
    trainings = jasmine.createSpyObj('TrainingService', ['list', 'create', 'update', 'delete']);
    trainings.list.and.returnValue(list instanceof Error ? throwError(() => list) : of(list));
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [TrainingsSectionComponent, TranslateModule.forRoot()],
      providers: [{ provide: TrainingService, useValue: trainings }, { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificationService, useValue: notifications }]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    fixture = TestBed.createComponent(TrainingsSectionComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const answer = (details: unknown) => dialog.open.and.returnValue({ afterClosed: () => of(details) } as never);

  it('lists each training with its certification, its funding due soon and its validity', () => {
    const element = create();
    expect(element.querySelector('[data-training="1"] h3')?.textContent).toContain('CKA · Linux Foundation');
    expect(element.querySelector('[data-training-funding]')?.classList).toContain('due');
    expect(element.querySelector('[data-training-validity]')?.classList).toContain('validity-VALID');
  });

  it('records, changes and deletes a training once confirmed', () => {
    create([]);
    const component = fixture.componentInstance;
    answer({ title: 'Scrum' });
    trainings.create.and.returnValue(of({ id: 2, title: 'Scrum', fundingDue: false }));
    component.add();
    expect(dialog.open).toHaveBeenCalledWith(TrainingDialogComponent, jasmine.objectContaining({ data: null }));
    expect(component.trainings.map(training => training.id)).toEqual([2]);

    answer({ title: 'Scrum Master' });
    trainings.update.and.returnValue(of({ id: 2, title: 'Scrum Master', fundingDue: false }));
    component.edit(component.trainings[0]);
    expect(trainings.update).toHaveBeenCalledWith(2, { title: 'Scrum Master' });
    expect(component.trainings[0].title).toBe('Scrum Master');

    confirm.confirm.and.returnValue(of(false));
    component.remove(component.trainings[0]);
    expect(trainings.delete).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    trainings.delete.and.returnValue(of(undefined));
    component.remove(component.trainings[0]);
    expect(component.trainings).toEqual([]);
  });

  it('keeps the list when the dialog is cancelled, and says when a call fails', () => {
    create(new Error('down'));
    expect(notifications.error).toHaveBeenCalledWith('trainings.loadError');
    const component = fixture.componentInstance;
    answer(undefined);
    component.add();
    expect(trainings.create).not.toHaveBeenCalled();
    answer({ title: 'X' });
    trainings.create.and.returnValue(throwError(() => new Error('down')));
    component.add();
    trainings.update.and.returnValue(throwError(() => new Error('down')));
    component.edit({ id: 9, title: 'Y', fundingDue: false });
    expect(notifications.error).toHaveBeenCalledWith('trainings.saveError');
    confirm.confirm.and.returnValue(of(true));
    trainings.delete.and.returnValue(throwError(() => new Error('down')));
    component.remove({ id: 9, title: 'Y', fundingDue: false });
    expect(notifications.error).toHaveBeenCalledWith('trainings.deleteError');
  });
});
