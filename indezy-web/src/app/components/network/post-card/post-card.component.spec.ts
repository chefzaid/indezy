import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkPost } from '../../../models';
import { PostCardComponent, segments } from './post-card.component';

describe('PostCardComponent', () => {
  const post: NetworkPost = { id: 4, text: 'Merci @leo_m pour #Kafka !\n<b>pas du HTML</b> mail@example.com', tags: ['kafka'],
    createdAt: '2026-11-14T10:00:00', own: true, author: { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null,
      headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false } };

  it('splits the text into plain text, mentions and tags, leaving an address or a word alone', () => {
    expect(segments('Merci @leo_m pour #Kafka-2 !')).toEqual([{ text: 'Merci ' }, { handle: 'leo_m' }, { text: ' pour ' }, { tag: 'Kafka-2' },
      { text: ' !' }]);
    expect(segments('mail@example.com et C#sharp')).toEqual([{ text: 'mail@example.com et C#sharp' }]);
    expect(segments('#lyon')).toEqual([{ tag: 'lyon' }]);
  });

  it('links the author and the members mentioned, shows the text as text, and lets its author delete it', () => {
    TestBed.configureTestingModule({ imports: [PostCardComponent, TranslateModule.forRoot()], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    const deleted = jasmine.createSpy('deleted');
    fixture.componentInstance.deleted.subscribe(deleted);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.author')?.getAttribute('href')).toBe('/m/camille');
    expect(element.querySelector('.mention')?.getAttribute('href')).toBe('/m/leo_m');
    expect(element.querySelector('.tag')?.textContent).toBe('#Kafka');
    expect(element.querySelector('.text b')).toBeNull();
    expect(element.querySelector('.text')?.textContent).toContain('<b>pas du HTML</b>');
    expect(element.querySelector('[data-post-actions]')).not.toBeNull();
    fixture.componentInstance.deleted.emit(post);
    expect(deleted).toHaveBeenCalledWith(post);
    fixture.componentRef.setInput('post', { ...post, own: false });
    fixture.detectChanges();
    expect(element.querySelector('[data-post-actions]')).toBeNull();
  });
});
