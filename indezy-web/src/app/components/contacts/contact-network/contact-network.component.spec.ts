import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ContactDto, ContactRelation } from '../../../models/contact.models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ContactNetworkComponent } from './contact-network.component';

describe('ContactNetworkComponent', () => {
  let fixture: ComponentFixture<ContactNetworkComponent>;
  let component: ContactNetworkComponent;
  let contacts: jasmine.SpyObj<ContactService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  const alice: ContactDto = { id: 3, firstName: 'Alice', lastName: 'Martin', clientId: 1, clientName: 'Acme', managerId: 9,
    managerName: 'Zoé Leroy' };
  const bob: ContactDto = { id: 4, firstName: 'Bob', clientId: 1, clientName: 'Acme', managerId: 3 };
  const nadia: ContactDto = { id: 5, firstName: 'Nadia', lastName: 'Kaci', clientId: 2, clientName: 'Esn' };
  const zoe: ContactDto = { id: 9, firstName: 'Zoé', lastName: 'Leroy', clientId: 1, clientName: 'Acme' };
  const withNadia: ContactRelation = { contactId: 3, otherId: 5, otherName: 'Nadia Kaci', otherClientId: 2, otherClientName: 'Esn',
    otherClientIsFinal: false, note: 'Account manager' };

  function create(everyone: Observable<ContactDto[]> = of([zoe, nadia, bob, alice]),
                  relations: Observable<ContactRelation[]> = of([withNadia]), contact: ContactDto = alice): void {
    contacts = jasmine.createSpyObj('ContactService', ['getForCurrentFreelance', 'getRelations', 'link', 'unlink']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    contacts.getForCurrentFreelance.and.returnValue(everyone);
    contacts.getRelations.and.returnValue(relations);
    TestBed.configureTestingModule({
      imports: [ContactNetworkComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ContactService, useValue: contacts },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    fixture = TestBed.createComponent(ContactNetworkComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('contact', contact);
    fixture.detectChanges();
  }

  function text(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  it('shows whom the contact reports to, its team and whom it works with', () => {
    create();

    expect(text()).toContain('Zoé Leroy');
    expect(component.team.map(member => member.firstName)).toEqual(['Bob']);
    expect(text()).toContain('Nadia Kaci');
    expect(text()).toContain('Esn · ESN');
    expect(text()).toContain('Account manager');
    expect(component.candidates.map(candidate => component.nameOf(candidate))).toEqual(['Bob', 'Zoé Leroy']);
  });

  it('says when nobody is above, below or linked', () => {
    create(of([]), of([]), { id: 8, firstName: 'Solo', clientId: 1 });

    expect(text()).toContain('contacts.network.nobody');
    expect(text()).toContain('contacts.network.noTeam');
    expect(text()).toContain('contacts.network.noRelations');
  });

  it('links someone with how they work together, then reloads the links', () => {
    create();
    contacts.link.and.returnValue(of(withNadia));

    component.link();
    expect(contacts.link).not.toHaveBeenCalled();
    component.form.setValue({ otherId: 9, note: '  Same team  ' });
    component.link();
    component.form.setValue({ otherId: 4, note: ' ' });
    component.link();

    expect(contacts.link.calls.allArgs()).toEqual([[3, 9, 'Same team'], [3, 4, undefined]]);
    expect(contacts.getRelations).toHaveBeenCalledTimes(3);
    expect(component.form.getRawValue()).toEqual({ otherId: null, note: '' });
  });

  it('unlinks someone and reports failures', () => {
    create();
    contacts.unlink.and.returnValues(of(undefined), throwError(() => new Error('down')));
    contacts.link.and.returnValue(throwError(() => new Error('down')));

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.relation button')!.click();
    expect(contacts.unlink).toHaveBeenCalledWith(3, 5);
    expect(contacts.getRelations).toHaveBeenCalledTimes(2);
    component.unlink(withNadia);
    expect(notifications.error).toHaveBeenCalledWith('contacts.network.unlinkError');

    component.form.setValue({ otherId: 9, note: '' });
    component.link();
    expect(notifications.error).toHaveBeenCalledWith('contacts.network.linkError');
    expect(component.saving).toBe(false);
  });

  it('reports a network that cannot be loaded', () => {
    create(throwError(() => new Error('down')), throwError(() => new Error('down')));

    expect(notifications.error).toHaveBeenCalledWith('contacts.network.loadError');
    expect(component.team).toEqual([]);
  });
});
