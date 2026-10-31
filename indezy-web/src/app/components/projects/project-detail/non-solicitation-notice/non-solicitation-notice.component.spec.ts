import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { NonSolicitation } from '../../../../models/project.models';
import { ProjectService } from '../../../../services/project/project.service';
import { NonSolicitationNoticeComponent } from './non-solicitation-notice.component';

describe('NonSolicitationNoticeComponent', () => {
  let fixture: ComponentFixture<NonSolicitationNoticeComponent>;
  const clause: NonSolicitation = { projectId: 42, missionId: 7, missionRole: 'Lead Java', middlemanName: 'ESN Conseil',
    clientName: 'Banque', months: 12, endsOn: '2027-03-31' };

  function render(answer: NonSolicitation[] | Error): HTMLElement {
    const projects = jasmine.createSpyObj('ProjectService', ['getNonSolicitations']);
    projects.getNonSolicitations.and.returnValue(answer instanceof Error ? throwError(() => answer) : of(answer));
    TestBed.configureTestingModule({
      imports: [NonSolicitationNoticeComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: ProjectService, useValue: projects }]
    });
    fixture = TestBed.createComponent(NonSolicitationNoticeComponent);
    fixture.componentRef.setInput('projectId', 42);
    fixture.componentRef.setInput('freelanceId', 1);
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
    expect(projects.getNonSolicitations).toHaveBeenCalledWith(1);
    return fixture.nativeElement as HTMLElement;
  }

  it('warns about the clause covering this opportunity, linking the mission', () => {
    const element = render([clause, { ...clause, projectId: 43, missionId: 8 }, { ...clause, missionId: 9, endsOn: null }]);
    expect(element.querySelectorAll('[data-non-solicitation]').length).toBe(2);
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/projects/7');
    expect(element.textContent).toContain('projects.nonSolicitation.until');
    expect(element.textContent).toContain('projects.nonSolicitation.afterMission');
  });

  it('says nothing when no clause covers it or the clauses cannot be read', () => {
    expect(render([{ ...clause, projectId: 43 }]).querySelector('[data-non-solicitation]')).toBeNull();
    TestBed.resetTestingModule();
    expect(render(new Error('down')).querySelector('[data-non-solicitation]')).toBeNull();
  });
});
