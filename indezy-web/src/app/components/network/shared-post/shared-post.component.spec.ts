import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { sharedPostId } from '../messages/message-thread.utils';
import { SharedPostComponent } from './shared-post.component';

describe('SharedPostComponent', () => {
  function create(read: Observable<NetworkPost>) {
    const network = jasmine.createSpyObj<NetworkService>('NetworkService', ['onePost']);
    network.onePost.and.returnValue(read);
    TestBed.configureTestingModule({ imports: [SharedPostComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: NetworkService, useValue: network }] });
    const fixture = TestBed.createComponent(SharedPostComponent);
    fixture.componentRef.setInput('postId', 4);
    fixture.detectChanges();
    return { element: fixture.nativeElement as HTMLElement, network };
  }

  it('shows the post sent as a card opening it, or says it is gone', () => {
    const post = { id: 4, text: 'x'.repeat(300), author: { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null } } as NetworkPost;
    const first = create(of(post));
    const network = first.network;
    let element = first.element;
    expect(network.onePost).toHaveBeenCalledWith(4, true);
    expect(element.querySelector('[data-shared-post]')?.getAttribute('href')).toBe('/network/posts/4');
    expect(element.querySelector('[data-shared-post]')?.textContent).toContain('Léo Martin');
    expect(element.querySelector('.text')?.textContent?.trim().endsWith('…')).toBeTrue();
    TestBed.resetTestingModule();
    ({ element } = create(throwError(() => new Error('gone'))));
    expect(element.querySelector('[data-shared-post-gone]')).not.toBeNull();
  });

  it('finds the post a message links to, on its own line or after a word', () => {
    expect(sharedPostId('À lire\nhttps://indezy.example/network/posts/42')).toBe(42);
    expect(sharedPostId('/network/posts/7')).toBe(7);
    expect(sharedPostId('voir https://indezy.example/network/posts/42/x')).toBeNull();
    expect(sharedPostId('Bonjour')).toBeNull();
    expect(sharedPostId(null)).toBeNull();
  });
});
