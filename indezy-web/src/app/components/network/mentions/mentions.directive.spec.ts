import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { MENTION_DELAY_MS, MentionsDirective } from './mentions.directive';

@Component({
  imports: [MentionsDirective, ReactiveFormsModule],
  template: '<textarea appMentions [formControl]="text"></textarea>'
})
class HostComponent {
  readonly text = new FormControl('', { nonNullable: true });
}

describe('MentionsDirective', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let fixture: ComponentFixture<HostComponent>;
  const card = (handle: string): MemberCard => ({ handle, firstName: 'Camille', lastName: 'Durand', avatar: null, headline: null, stack: [],
    city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });

  function field(): HTMLTextAreaElement {
    return fixture.nativeElement.querySelector('textarea');
  }

  function type(text: string): void {
    field().value = text;
    field().setSelectionRange(text.length, text.length);
    field().dispatchEvent(new Event('input'));
    tick(MENTION_DELAY_MS);
  }

  function key(name: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key: name, cancelable: true });
    field().dispatchEvent(event);
    return event;
  }

  const choices = (): string[] => Array.from(document.querySelectorAll('[data-mention-choice]'))
    .map(item => item.getAttribute('data-mention-choice')!);

  beforeEach(() => {
    network = jasmine.createSpyObj('NetworkService', ['mentions', 'suggestTags']);
    network.mentions.and.returnValue(of([card('camille'), card('cam_d')]));
    network.suggestTags.and.returnValue(of(['kafka', 'kafka-streams']));
    TestBed.configureTestingModule({ imports: [HostComponent, TranslateModule.forRoot()], providers: [{ provide: NetworkService, useValue: network }] });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('finds what is being typed after an @ or a #, at the start or after a space', () => {
    expect(MentionsDirective.token('Merci @cam', 10)).toEqual({ sigil: '@', typed: 'cam' });
    expect(MentionsDirective.token('#Kaf', 4)).toEqual({ sigil: '#', typed: 'Kaf' });
    expect(MentionsDirective.token('(#java', 6)).toEqual({ sigil: '#', typed: 'java' });
    expect(MentionsDirective.token('mail@example', 12)).toBeNull();
    expect(MentionsDirective.token('Merci @', 7)).toBeNull();
    expect(MentionsDirective.token('@cam et la suite', 4)).toEqual({ sigil: '@', typed: 'cam' });
  });

  it('offers the members to mention, moves with the arrows and writes the one picked with Enter', fakeAsync(() => {
    expect(field().getAttribute('aria-autocomplete')).toBe('list');
    type('Merci @cam');
    expect(network.mentions).toHaveBeenCalledWith('cam');
    expect(choices()).toEqual(['camille', 'cam_d']);
    expect(field().getAttribute('aria-activedescendant')).toMatch(/-0$/);
    key('ArrowDown');
    expect(field().getAttribute('aria-activedescendant')).toMatch(/-1$/);
    key('ArrowDown');
    key('ArrowUp');
    expect(document.querySelector('[data-mention-choice="cam_d"]')?.getAttribute('aria-selected')).toBe('true');
    expect(key('a').defaultPrevented).toBeFalse();
    expect(key('Enter').defaultPrevented).toBeTrue();
    expect(fixture.componentInstance.text.value).toBe('Merci @cam_d ');
    tick(MENTION_DELAY_MS);
    expect(choices()).toEqual([]);
    expect(field().hasAttribute('aria-controls')).toBeFalse();
    expect(key('Enter').defaultPrevented).toBeFalse();
  }));

  it('offers the tags in use, picked with a click, and closes on Escape or when the field is left', fakeAsync(() => {
    type('Retour sur #kaf');
    expect(network.suggestTags).toHaveBeenCalledWith('kaf');
    (document.querySelector('[data-mention-choice="kafka-streams"]') as HTMLElement).click();
    expect(fixture.componentInstance.text.value).toBe('Retour sur #kafka-streams ');

    type('#ja');
    expect(choices().length).toBe(2);
    key('Escape');
    expect(choices()).toEqual([]);
    type('#ja');
    key('Tab');
    expect(fixture.componentInstance.text.value).toBe('#kafka ');
    type('#ja');
    field().dispatchEvent(new Event('blur'));
    tick(MENTION_DELAY_MS);
    expect(choices()).toEqual([]);
  }));

  it('offers nothing when the server cannot answer, or when nothing is being typed after a sigil', fakeAsync(() => {
    network.mentions.and.returnValue(throwError(() => new Error('down')));
    type('@cam');
    expect(choices()).toEqual([]);
    type('Bonjour');
    expect(network.suggestTags).not.toHaveBeenCalled();
    const directive = fixture.debugElement.children[0].injector.get(MentionsDirective);
    directive.pick({ sigil: '@', value: 'camille', member: null });
    expect(fixture.componentInstance.text.value).toBe('Bonjour');
  }));
});
