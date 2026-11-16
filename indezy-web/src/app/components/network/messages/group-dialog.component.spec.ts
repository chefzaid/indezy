import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, NetworkMembership } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { GroupDialogComponent, GroupRequest } from './group-dialog.component';

describe('GroupDialogComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let close: jasmine.Spy;
  const card = (handle: string, firstName: string): MemberCard => ({ handle, firstName, lastName: 'Durand', avatar: null, headline: null,
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });

  function create(request: GroupRequest, followers = of([card('leo_m', 'Léo'), card('ines_b', 'Inès'), card('paul_d', 'Paul')]))
    : ComponentFixture<GroupDialogComponent> {
    network = jasmine.createSpyObj('NetworkService', ['membership', 'followers']);
    network.membership.and.returnValue(of({ handle: 'camille' } as NetworkMembership));
    network.followers.and.returnValue(followers);
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [GroupDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: NetworkService, useValue: network },
        { provide: MAT_DIALOG_DATA, useValue: request }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(GroupDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('names a new group and picks its members among the reader\'s followers, as many as there is room for', () => {
    const fixture = create({ title: null, present: ['paul_d'], room: 2 });
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(network.followers).toHaveBeenCalledWith('camille');
    expect(component.followers?.map(member => member.handle)).toEqual(['leo_m', 'ines_b']);
    component.filter.setValue('inè');
    expect(component.shown.map(member => member.handle)).toEqual(['ines_b']);
    component.filter.setValue('');
    expect(component.valid).toBeFalse();
    component.toggle('leo_m', true);
    component.toggle('ines_b', true);
    component.toggle('paul_d', true);
    expect([...component.chosen]).toEqual(['leo_m', 'ines_b']);
    component.toggle('ines_b', false);
    component.done();
    expect(close).not.toHaveBeenCalled();
    component.title.setValue('  Mission Kafka ');
    fixture.detectChanges();
    expect(element.querySelector('[data-group-title]')).not.toBeNull();
    component.done();
    expect(close).toHaveBeenCalledWith({ title: 'Mission Kafka', handles: ['leo_m'] });
  });

  it('adds members to a named group, and says when there is no follower to add', () => {
    let fixture = create({ title: 'Mission Kafka', present: [], room: 5 });
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-group-title]')).toBeNull();
    fixture.componentInstance.toggle('leo_m', true);
    expect(fixture.componentInstance.valid).toBeTrue();
    TestBed.resetTestingModule();
    fixture = create({ title: 'Mission Kafka', present: [], room: 5 }, throwError(() => new Error('down')));
    expect(fixture.componentInstance.followers).toEqual([]);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-group-none]')).not.toBeNull();
  });

  it('opens as a dialog and answers what was chosen', () => {
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of({ title: 'X', handles: ['leo_m'] }) } as never);
    let answer: unknown;
    GroupDialogComponent.open(dialog, { title: null, present: [], room: 19 }).subscribe(choice => answer = choice);
    expect(answer).toEqual({ title: 'X', handles: ['leo_m'] });
  });
});
