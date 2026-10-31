import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ProjectDto } from '../../../../models/project.models';
import { NotificationService } from '../../../../services/notification/notification.service';
import { ProjectService } from '../../../../services/project/project.service';
import { ContractChecklistComponent } from './contract-checklist.component';

describe('ContractChecklistComponent', () => {
  let fixture: ComponentFixture<ContractChecklistComponent>;
  let projects: jasmine.SpyObj<ProjectService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const mission = { id: 42, role: 'Lead Java', dailyRate: 650, freelanceId: 1, status: 'WON',
    contractChecks: [{ point: 'NOTICE_PERIOD', checked: true, note: '15 jours' }] } as ProjectDto;

  beforeEach(() => {
    projects = jasmine.createSpyObj('ProjectService', ['replaceContractChecks']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [ContractChecklistComponent, TranslateModule.forRoot()],
      providers: [{ provide: ProjectService, useValue: projects }, { provide: NotificationService, useValue: notifications }]
    });
    fixture = TestBed.createComponent(ContractChecklistComponent);
    fixture.componentRef.setInput('project', mission);
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
  });

  it('lists every point, the saved ones ticked with their note', () => {
    const component = fixture.componentInstance;
    expect(component.checks.length).toBe(7);
    expect(component.checks[1]).toEqual({ point: 'NOTICE_PERIOD', checked: true, note: '15 jours' });
    expect(component.checkedCount).toBe(1);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-contract-count]')?.textContent).toBe('1/7');
  });

  it('saves the ticked or noted points when one is ticked', () => {
    projects.replaceContractChecks.and.returnValue(of({ ...mission, contractChecks: [
      { point: 'PAYMENT_TERMS', checked: true, note: null }, { point: 'NOTICE_PERIOD', checked: true, note: '15 jours' }] }));
    fixture.componentInstance.toggle('PAYMENT_TERMS', true);
    expect(projects.replaceContractChecks).toHaveBeenCalledWith(42, [
      { point: 'PAYMENT_TERMS', checked: true, note: null }, { point: 'NOTICE_PERIOD', checked: true, note: '15 jours' }]);
    expect(fixture.componentInstance.project.contractChecks?.length).toBe(2);
  });

  it('saves a note only once it changed', () => {
    projects.replaceContractChecks.and.returnValue(of(mission));
    fixture.componentInstance.saveNote('NOTICE_PERIOD', ' 15 jours ');
    expect(projects.replaceContractChecks).not.toHaveBeenCalled();
    fixture.componentInstance.saveNote('RATE_REVISION', 'Revue annuelle');
    expect(projects.replaceContractChecks).toHaveBeenCalledWith(42, [
      { point: 'NOTICE_PERIOD', checked: true, note: '15 jours' }, { point: 'RATE_REVISION', checked: false, note: 'Revue annuelle' }]);
  });

  it('puts the saved checklist back when it cannot be saved', () => {
    projects.replaceContractChecks.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggle('NOTICE_PERIOD', false);
    expect(notifications.error).toHaveBeenCalledWith('projects.contract.saveError');
    expect(fixture.componentInstance.checks[1].checked).toBeTrue();
  });
});
