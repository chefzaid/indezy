import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { Conversation, MemberCard, Message, NetworkMembership, NetworkPost } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkService } from '../../../services/network/network.service';
import { SEND_ROOM, SendPostDialogComponent, postLink } from './send-post-dialog.component';

describe('SendPostDialogComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let messages: jasmine.SpyObj<NetworkMessagesService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<SendPostDialogComponent, number>>;
  const member = (handle: string, firstName: string): MemberCard => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: null,
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const conversation = (id: number, changes: Partial<Conversation>): Conversation => ({ id, with: null, group: null, last: null, unread: 0,
    muted: false, archived: false, closed: null, lastMessageAt: null, ...changes });
  const post = { id: 4, text: 'Retour sur Kafka', author: member('camille', 'Camille') } as NetworkPost;

  function create() {
    network = jasmine.createSpyObj('NetworkService', ['membership', 'followers', 'following']);
    network.membership.and.returnValue(of({ handle: 'camille' } as NetworkMembership));
    network.followers.and.returnValue(of([member('leo_m', 'Léo'), member('zoe_l', 'Zoé')]));
    network.following.and.returnValue(of([member('leo_m', 'Léo'), member('ines_b', 'Inès')]));
    messages = jasmine.createSpyObj('NetworkMessagesService', ['conversations', 'open', 'send']);
    messages.conversations.and.returnValue(of([conversation(1, { with: member('leo_m', 'Léo') }),
      conversation(2, { group: { title: 'Collectif Kafka', members: [], size: 4, creator: null, admin: false } }),
      conversation(3, { with: member('paul_d', 'Paul'), closed: 'BLOCKED' })]));
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [SendPostDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MAT_DIALOG_DATA, useValue: post },
        { provide: MatDialogRef, useValue: dialogRef }, { provide: NetworkService, useValue: network },
        { provide: NetworkMessagesService, useValue: messages }]
    });
    const fixture = TestBed.createComponent(SendPostDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('offers the open conversations, then the members one can write to without one, and filters them', () => {
    const component = create().componentInstance;
    expect(component.targets?.map(target => target.key)).toEqual(['c1', 'c2', 'mines_b', 'mzoe_l']);
    expect(component.targets?.[1].label).toBe('Collectif Kafka');
    component.filter.setValue('zo');
    expect(component.shown.map(target => target.key)).toEqual(['mzoe_l']);
  });

  it("sends the post's link with a word to each choice, opening a conversation with a member, and says when one fails", () => {
    const fixture = create();
    const component = fixture.componentInstance;
    messages.send.and.returnValue(of({} as Message));
    messages.open.and.returnValue(of(conversation(9, { with: member('ines_b', 'Inès') })));
    component.toggle(component.targets![0]);
    component.toggle(component.targets![2]);
    component.note.setValue('  À lire  ');
    component.send();
    expect(messages.open).toHaveBeenCalledWith('ines_b');
    expect(messages.send).toHaveBeenCalledWith(1, `À lire\n${postLink(4)}`);
    expect(messages.send).toHaveBeenCalledWith(9, `À lire\n${postLink(4)}`);
    expect(dialogRef.close).toHaveBeenCalledWith(2);

    dialogRef.close.calls.reset();
    messages.send.and.returnValue(throwError(() => new Error('down')));
    component.note.setValue('');
    component.send();
    expect(messages.send).toHaveBeenCalledWith(1, postLink(4));
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(component.failed).toBeTrue();
    expect(component.sending).toBeFalse();
  });

  it('keeps to ten choices, and opens as a dialog', () => {
    const component = create().componentInstance;
    component.targets = Array.from({ length: SEND_ROOM + 2 }, (_, index) => ({ key: `k${index}`, label: `${index}`, conversation: null,
      member: null }));
    component.targets.forEach(target => component.toggle(target));
    expect(component.chosen.size).toBe(SEND_ROOM);
    component.toggle(component.targets[0]);
    expect(component.chosen.has('k0')).toBeFalse();
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(1) } as never);
    SendPostDialogComponent.open(dialog, post).subscribe(sent => expect(sent).toBe(1));
    expect(dialog.open).toHaveBeenCalled();
  });
});
