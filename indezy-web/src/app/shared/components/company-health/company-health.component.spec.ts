import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { CompanyHealth } from '../../../models/client.models';
import { ClientService } from '../../../services/client/client.service';
import { CompanyHealthComponent } from './company-health.component';

describe('CompanyHealthComponent', () => {
  let fixture: ComponentFixture<CompanyHealthComponent>;

  function render(answer: CompanyHealth | Error): HTMLElement {
    const clients = jasmine.createSpyObj('ClientService', ['getHealth']);
    clients.getHealth.and.returnValue(answer instanceof Error ? throwError(() => answer) : of(answer));
    TestBed.configureTestingModule({
      imports: [CompanyHealthComponent, TranslateModule.forRoot()],
      providers: [{ provide: ClientService, useValue: clients }]
    });
    fixture = TestBed.createComponent(CompanyHealthComponent);
    fixture.componentRef.setInput('clientId', 3);
    fixture.componentRef.setInput('name', 'Acme');
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
    expect(clients.getHealth).toHaveBeenCalledWith(3);
    return fixture.nativeElement as HTMLElement;
  }

  it('warns of a closed company and of proceedings with their date', () => {
    const element = render({ checked: true, closed: true, procedure: 'LIQUIDATION', procedureOn: '2026-05-03' });
    expect(element.querySelector('[data-company-health]')?.textContent).toContain('clients.health.closed');
    expect(element.textContent).toContain('clients.health.procedure');
    expect(element.textContent).toContain('clients.health.announcedOn');
  });

  it('says nothing of a healthy or unchecked company, nor when the check fails', () => {
    expect(render({ checked: true, closed: false }).querySelector('[data-company-health]')).toBeNull();
    TestBed.resetTestingModule();
    expect(render({ checked: false, closed: false }).querySelector('[data-company-health]')).toBeNull();
    TestBed.resetTestingModule();
    expect(render(new Error('down')).querySelector('[data-company-health]')).toBeNull();
  });

  it('warns of proceedings without a date', () => {
    const element = render({ checked: true, closed: false, procedure: 'SAUVEGARDE', procedureOn: null });
    expect(element.textContent).toContain('clients.health.procedure');
    expect(element.textContent).not.toContain('clients.health.announcedOn');
  });
});
