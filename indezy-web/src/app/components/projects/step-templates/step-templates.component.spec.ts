import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { PipelineTemplate, ProjectDto, StepStatus } from '../../../models';
import { PipelineTemplateService } from '../../../services/pipeline-template/pipeline-template.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { TemplateSaveDialogComponent } from '../template-save-dialog/template-save-dialog.component';
import { StepTemplatesComponent } from './step-templates.component';
import en from '../../../../assets/i18n/en.json';

describe('StepTemplatesComponent', () => {
  let templates: jasmine.SpyObj<PipelineTemplateService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  const project: ProjectDto = { id: 8, role: 'Java developer', dailyRate: 650, clientId: 3, middlemanId: 5, clientName: 'Airbus', middlemanName: 'Alten' };
  const usual: PipelineTemplate = { id: 1, name: 'Usual', steps: [{ title: 'Call', checklist: [] }] };
  const alten: PipelineTemplate = { id: 2, name: 'Alten process', clientId: 5, clientName: 'Alten',
    steps: [{ title: 'Call', checklist: [] }, { title: 'Test', checklist: ['Revise'] }] };
  const other: PipelineTemplate = { id: 3, name: 'Capgemini', clientId: 9, clientName: 'Capgemini', steps: [] };

  function create(): ComponentFixture<StepTemplatesComponent> {
    templates = jasmine.createSpyObj('PipelineTemplateService', ['list', 'apply', 'delete']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'successText', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [StepTemplatesComponent, TranslateModule.forRoot()],
      providers: [
        { provide: PipelineTemplateService, useValue: templates },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: MatDialog, useValue: dialog }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(StepTemplatesComponent);
    fixture.componentRef.setInput('project', project);
    fixture.componentRef.setInput('hasSteps', true);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the templates of the opportunity\'s client or ESN first, then the others by name', () => {
    const component = create().componentInstance;
    templates.list.and.returnValue(of([usual, other, alten]));

    component.load();

    expect(component.templates.map(template => template.name)).toEqual(['Alten process', 'Capgemini', 'Usual']);
    expect(component.loaded).toBeTrue();
  });

  it('applies a template to the opportunity and says how many steps it added', () => {
    const component = create().componentInstance;
    templates.apply.and.returnValue(of([{ title: 'Call', status: StepStatus.TO_PLAN }, { title: 'Test', status: StepStatus.TO_PLAN }]));
    let applied = 0;
    component.applied.subscribe(() => applied++);

    component.apply(alten);

    expect(templates.apply).toHaveBeenCalledWith(2, 8);
    expect(applied).toBe(1);
    expect(notifications.successText).toHaveBeenCalledWith('2 step(s) added from "Alten process"', 3000);
  });

  it('opens the save dialog with the opportunity', () => {
    const component = create().componentInstance;

    component.save();

    expect(dialog.open).toHaveBeenCalledWith(TemplateSaveDialogComponent, { data: project, autoFocus: 'first-tabbable' });
  });

  it('deletes a template once confirmed, without applying it', () => {
    const component = create().componentInstance;
    component.templates = [usual, alten];
    templates.delete.and.returnValue(of(undefined));
    confirm.confirm.and.returnValues(of(false), of(true));
    const event = new MouseEvent('click');
    const stop = spyOn(event, 'stopPropagation');

    component.remove(usual, event);
    component.remove(usual, event);

    expect(stop).toHaveBeenCalled();
    expect(templates.delete).toHaveBeenCalledOnceWith(1);
    expect(component.templates).toEqual([alten]);
    expect(notifications.success).toHaveBeenCalledWith('steps.templates.deleted');
  });

  it('says when loading, applying or deleting fails', () => {
    const component = create().componentInstance;
    templates.list.and.returnValue(throwError(() => new Error('down')));
    templates.apply.and.returnValue(throwError(() => new Error('down')));
    templates.delete.and.returnValue(throwError(() => new Error('down')));
    confirm.confirm.and.returnValue(of(true));

    component.load();
    component.apply(usual);
    component.remove(usual, new MouseEvent('click'));

    expect(notifications.error.calls.allArgs()).toEqual([
      ['steps.templates.loadError'], ['steps.templates.applyError'], ['steps.templates.deleteError']
    ]);
  });
});
