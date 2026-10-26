import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ContactDto, ContactRelation } from '../../../models/contact.models';
import { ContactService } from '../../../services/contact/contact.service';
import { ClientOrgChartComponent, OrgNode, buildOrgChart } from './client-org-chart.component';

describe('ClientOrgChartComponent', () => {
  const cto: ContactDto = { id: 1, firstName: 'Zoé', lastName: 'Leroy', clientId: 1 };
  const lead: ContactDto = { id: 2, firstName: 'Alice', clientId: 1, managerId: 1 };
  const dev: ContactDto = { id: 3, firstName: 'Bob', clientId: 1, managerId: 2 };
  const buyer: ContactDto = { id: 4, firstName: 'Carl', clientId: 1, managerId: 1 };
  const outsider: ContactDto = { id: 5, firstName: 'Dan', clientId: 1, managerId: 40, managerName: 'Paul Roy' };
  const withEsn: ContactRelation = { contactId: 2, otherId: 9, otherName: 'Nadia Kaci', otherClientName: 'Esn', note: 'Account manager' };

  function shape(nodes: OrgNode[]): unknown[] {
    return nodes.map(node => node.reports.length ? { [node.contact.firstName]: shape(node.reports) } : node.contact.firstName);
  }

  it('nests the contacts by whom they report to, by name, with their links elsewhere', () => {
    const roots = buildOrgChart([dev, buyer, outsider, lead, cto], [withEsn]);

    expect(shape(roots)).toEqual(['Dan', { Zoé: [{ Alice: ['Bob'] }, 'Carl'] }]);
    expect(roots[0].externalManager).toBe('Paul Roy');
    expect(roots[1].reports[0].worksWith).toEqual([withEsn]);
    expect(roots[1].externalManager).toBeUndefined();
  });

  it('shows everyone flat when stored reporting lines loop', () => {
    const roots = buildOrgChart([{ ...cto, managerId: 2 }, lead], []);

    expect(shape(roots)).toEqual(['Alice', 'Zoé']);
  });

  it('draws the chart with links to each person, and without links when they cannot be loaded', () => {
    const contacts = jasmine.createSpyObj('ContactService', ['getClientRelations']);
    contacts.getClientRelations.and.returnValues(of([withEsn]), throwError(() => new Error('down')));
    TestBed.configureTestingModule({
      imports: [ClientOrgChartComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: ContactService, useValue: contacts }]
    });
    const fixture = TestBed.createComponent(ClientOrgChartComponent);
    fixture.componentRef.setInput('clientId', 1);
    fixture.componentRef.setInput('contacts', [cto, lead, outsider]);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(contacts.getClientRelations).toHaveBeenCalledWith(1);
    expect(Array.from(element.querySelectorAll('.org-name')).map(name => name.textContent!.trim())).toEqual(['Dan', 'Zoé Leroy', 'Alice']);
    expect(element.querySelector('.org-link')!.textContent).toContain('Nadia Kaci · Esn');
    expect(element.querySelector('.org-link')!.getAttribute('title')).toBe('Account manager');
    expect(element.querySelector('.org-external')!.textContent).toContain('clients.orgChart.reportsTo');

    fixture.componentRef.setInput('contacts', [cto]);
    fixture.detectChanges();
    expect(element.querySelectorAll('.org-link').length).toBe(0);
  });
});
