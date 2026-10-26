import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import { PipelineTemplate, ProjectDto } from '../../../models';
import { PipelineTemplateService } from '../../../services/pipeline-template/pipeline-template.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TemplateSaveDialogComponent } from './template-save-dialog.component';

describe('TemplateSaveDialogComponent', () => {
  let templates: jasmine.SpyObj<PipelineTemplateService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<TemplateSaveDialogComponent, PipelineTemplate>>;
  const project: ProjectDto = { id: 8, role: 'Java developer', dailyRate: 650, clientId: 3, clientName: 'Airbus', middlemanId: 5, middlemanName: 'Alten' };
  const saved: PipelineTemplate = { id: 1, name: 'Alten', clientId: 5, steps: [] };

  function create(over: Partial<ProjectDto> = {}): TemplateSaveDialogComponent {
    templates = jasmine.createSpyObj('PipelineTemplateService', ['create']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    return new TemplateSaveDialogComponent(new FormBuilder(), { ...project, ...over }, dialogRef, templates, notifications);
  }

  it('offers the ESN first, then the client, named after the ESN', () => {
    const component = create();

    expect(component.choices).toEqual([{ id: 5, name: 'Alten' }, { id: 3, name: 'Airbus' }]);
    expect(component.form.value).toEqual({ name: 'Alten', clientId: 5 });
  });

  it('names a template after the client of an opportunity without ESN, and none without client', () => {
    expect(create({ middlemanId: undefined, middlemanName: undefined }).form.value).toEqual({ name: 'Airbus', clientId: 3 });
    const orphan = create({ middlemanId: undefined, middlemanName: undefined, clientId: undefined, clientName: undefined });
    expect(orphan.choices).toEqual([]);
    expect(orphan.form.value).toEqual({ name: '', clientId: null });
  });

  it('saves the template for the chosen client, or for anyone, and closes with it', () => {
    const component = create();
    templates.create.and.returnValue(of(saved));
    component.form.setValue({ name: '  Alten usual ', clientId: null });

    component.save();

    expect(templates.create).toHaveBeenCalledWith('Alten usual', 8, undefined);
    expect(notifications.success).toHaveBeenCalledWith('steps.templates.saved');
    expect(dialogRef.close).toHaveBeenCalledWith(saved);
  });

  it('marks a taken name on its field and reports other refusals', () => {
    const component = create();
    templates.create.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'A pipeline template is already named "Alten"' } })),
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'A workspace keeps at most 50 pipeline templates' } })),
      throwError(() => new HttpErrorResponse({ status: 500 })));

    component.save();
    expect(component.form.get('name')!.hasError('taken')).toBeTrue();
    expect(component.saving).toBeFalse();

    component.form.get('name')!.setValue('Other');
    component.save();
    component.save();
    expect(notifications.error.calls.allArgs()).toEqual([['steps.templates.limit'], ['steps.templates.saveError']]);
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('saves nothing without a name, nor twice at once', () => {
    const component = create();
    component.form.get('name')!.setValue('');
    component.save();
    expect(templates.create).not.toHaveBeenCalled();
    expect(component.form.get('name')!.touched).toBeTrue();

    component.form.get('name')!.setValue('Alten');
    templates.create.and.returnValue(new Subject<PipelineTemplate>());
    component.save();
    component.save();
    expect(templates.create).toHaveBeenCalledTimes(1);
  });
});
