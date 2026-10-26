import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ContactDto } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ContactDetailComponent } from './contact-detail.component';

describe('ContactDetailComponent', () => {
  let contacts: jasmine.SpyObj<ContactService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let navigate: jasmine.Spy;

  const alice: ContactDto = { id: 3, firstName: 'Alice', lastName: 'Martin', email: 'alice@acme.fr', phone: '0601', clientId: 1,
    clientName: 'Acme', notes: 'Prefers calls' };

  function create(contact: Observable<ContactDto | null> = of(alice), id = 3): ComponentFixture<ContactDetailComponent> {
    contacts = jasmine.createSpyObj('ContactService', ['getById', 'delete', 'getInteractions', 'getForCurrentFreelance', 'getRelations']);
    contacts.getForCurrentFreelance.and.returnValue(of([]));
    contacts.getRelations.and.returnValue(of([]));
    contacts.getInteractions.and.returnValue(of({ interactions: [], asked: 0, answered: 0 }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    contacts.getById.and.returnValue(contact as Observable<ContactDto>);
    TestBed.configureTestingModule({
      imports: [ContactDetailComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { params: of(id ? { id } : {}) } },
        { provide: ContactService, useValue: contacts },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ProjectService, useValue: { getByFreelanceId: () => of([]) } }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(ContactDetailComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ContactDetailComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  it('shows the contact with its links, client and notes', () => {
    const fixture = create();

    expect(contacts.getById).toHaveBeenCalledWith(3);
    const page = text(fixture);
    expect(page).toContain('Alice Martin');
    expect(page).toContain('Acme');
    expect(page).toContain('Prefers calls');
    expect(fixture.nativeElement.querySelector('a[href="mailto:alice@acme.fr"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="tel:0601"]')).not.toBeNull();
    expect(fixture.componentInstance.isLoading).toBeFalse();
  });

  it('says when the email and phone are unknown', () => {
    const fixture = create(of({ id: 4, firstName: 'Bob', clientId: 1 }));

    expect(text(fixture)).toContain('common.notSpecified');
    expect(fixture.componentInstance.getFullName()).toBe('Bob');
    expect(fixture.nativeElement.querySelector('a[href^="mailto:"]')).toBeNull();
  });

  it('loads nothing without an id', () => {
    const component = create(of(alice), 0).componentInstance;

    expect(contacts.getById).not.toHaveBeenCalled();
    expect(component.getFullName()).toBe('');
    component.onEdit();
    expect(navigate).not.toHaveBeenCalled();
    component.onDelete();
    expect(notifications.error).toHaveBeenCalledWith('errors.missingContactId');
  });

  it('returns to the list when the contact is missing', () => {
    create(of(null));

    expect(notifications.error).toHaveBeenCalledWith('errors.contactNotFound');
    expect(navigate).toHaveBeenCalledWith(['/contacts']);
  });

  it('returns to the list when the contact cannot be loaded', () => {
    spyOn(console, 'error');
    create(throwError(() => ({ status: 404 })));
    expect(notifications.error).toHaveBeenCalledWith('errors.contactNotFound');
    expect(navigate).toHaveBeenCalledWith(['/contacts']);

    TestBed.resetTestingModule();
    const component = create(throwError(() => ({ status: 500 }))).componentInstance;
    expect(notifications.error).toHaveBeenCalledWith('errors.loadingContact');
    expect(component.isLoading).toBeFalse();
  });

  it('goes back to the list or edits the contact from the header buttons', () => {
    const fixture = create();
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button');
    const byLabel = (label: string): HTMLButtonElement =>
      Array.from(buttons).find(button => button.textContent!.includes(label))!;
    byLabel('common.back').click();
    expect(navigate).toHaveBeenCalledWith(['/contacts']);
    byLabel('common.edit').click();
    expect(navigate).toHaveBeenCalledWith(['/contacts', 3, 'edit']);
  });

  describe('deleting the contact', () => {
    it('keeps the contact when the deletion is not confirmed', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(false));

      component.onDelete();

      expect(confirm.confirm).toHaveBeenCalledWith({
        messageKey: 'contacts.confirmDelete', messageParams: { name: 'Alice Martin' }, danger: true
      });
      expect(contacts.delete).not.toHaveBeenCalled();
    });

    it('deletes the contact and returns to the list', () => {
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      contacts.delete.and.returnValue(of(undefined));

      component.onDelete();

      expect(contacts.delete).toHaveBeenCalledWith(3);
      expect(notifications.success).toHaveBeenCalledWith('contacts.deleteSuccess');
      expect(navigate).toHaveBeenCalledWith(['/contacts']);
    });

    it('reports a failed deletion', () => {
      spyOn(console, 'error');
      const component = create().componentInstance;
      confirm.confirm.and.returnValue(of(true));
      contacts.delete.and.returnValue(throwError(() => new Error('down')));

      component.onDelete();

      expect(notifications.error).toHaveBeenCalledWith('errors.deletingContact');
      expect(navigate).not.toHaveBeenCalled();
    });
  });
});
