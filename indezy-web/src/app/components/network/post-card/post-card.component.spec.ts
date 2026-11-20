import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { PostImpressionsService } from '../../../services/network/post-impressions.service';
import { SendPostDialogComponent } from '../send-post/send-post-dialog.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { PostCardComponent, segments } from './post-card.component';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';

describe('PostCardComponent', () => {
  const post: NetworkPost = { id: 4, text: 'Merci @leo_m pour #Kafka !\n<b>pas du HTML</b> mail@example.com', tags: ['kafka'],
    createdAt: '2026-11-14T10:00:00', own: true, likes: 2, liked: false, comments: 3, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST' as const, details: null, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null, preview: null, author: { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null,
      headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false } };

  it('splits the text into plain text, mentions and tags, leaving an address or a word alone', () => {
    expect(segments('Merci @leo_m pour #Kafka-2 !')).toEqual([{ text: 'Merci ' }, { handle: 'leo_m' }, { text: ' pour ' }, { tag: 'Kafka-2' },
      { text: ' !' }]);
    expect(segments('mail@example.com et C#sharp')).toEqual([{ text: 'mail@example.com et C#sharp' }]);
    expect(segments('#lyon')).toEqual([{ tag: 'lyon' }]);
  });

  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let impressions: jasmine.SpyObj<PostImpressionsService>;

  function configure(): void {
    network = jasmine.createSpyObj('NetworkService', ['react', 'likers', 'closeComments', 'comments', 'editPost', 'postVersions', 'bookmark',
      'repost', 'repostsOff', 'vote', 'postStats', 'pinPost']);
    network.comments.and.returnValue(of([]));
    impressions = jasmine.createSpyObj('PostImpressionsService', ['seen']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({ imports: [PostCardComponent, TranslateModule.forRoot()], providers: [provideRouter([]),
      { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
      { provide: PostImpressionsService, useValue: impressions }] });
  }

  it('pins the author\'s post on their page, then unpins it, and says when it cannot', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const changes: boolean[] = [];
    component.pinChanged.subscribe(pinned => changes.push(pinned));
    network.pinPost.and.returnValue(of(undefined));
    component.togglePin();
    fixture.detectChanges();
    expect(network.pinPost).toHaveBeenCalledWith(4, true);
    expect(fixture.nativeElement.querySelector('[data-post-pinned]')).not.toBeNull();
    expect(notifications.success).toHaveBeenCalledWith('network.post.pinnedDone');
    component.togglePin();
    expect(network.pinPost).toHaveBeenCalledWith(4, false);
    expect(changes).toEqual([true, false]);
    network.pinPost.and.returnValue(throwError(() => new Error('down')));
    component.togglePin();
    expect(component.pinned).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('network.post.pinError');
  });

  it('names the community a post was posted in, but in that community\'s own feed', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, community: { slug: 'kafka', name: 'Kafka' } });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-post-community]')?.getAttribute('href')).toBe('/network/communities/kafka');
    fixture.componentRef.setInput('inCommunity', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-post-community]')).toBeNull();
  });

  it('offers Not interested on another member\'s post in For you only', async () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, own: false });
    fixture.componentRef.setInput('dismissible', true);
    fixture.detectChanges();
    const hidden: NetworkPost[] = [];
    fixture.componentInstance.dismissed.subscribe(item => hidden.push(item));
    (fixture.nativeElement.querySelector('[data-post-actions]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const item = document.querySelector('[data-post-not-interested]') as HTMLButtonElement;
    expect(item).not.toBeNull();
    item.click();
    expect(hidden.map(found => found.id)).toEqual([4]);
  });

  it('sends the post in a message, saying in how many conversations', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const open = spyOn(SendPostDialogComponent, 'open').and.returnValue(of(3));
    (fixture.nativeElement.querySelector('[data-post-send]') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledWith(jasmine.anything(), post);
    expect(notifications.success).toHaveBeenCalledWith('network.send.done', undefined, { count: 3 });
    open.and.returnValue(of(undefined));
    fixture.componentInstance.send();
    expect(notifications.success).toHaveBeenCalledTimes(1);
  });

  it('shows its author the post\'s analytics, and counts another member\'s post once half of it is seen', async () => {
    configure();
    let fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    let element: HTMLElement = fixture.nativeElement;
    network.postStats.and.returnValue(of({ impressions: 120, reached: 45, saves: 3 }));
    (element.querySelector('[data-post-insights]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.postStats).toHaveBeenCalledWith(4);
    expect(element.querySelector('[data-post-figures]')?.textContent).toContain('120');
    expect(element.querySelector('[data-post-figures]')?.textContent).toContain('45');
    (element.querySelector('[data-post-insights]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-post-figures]')).toBeNull();
    network.postStats.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggleInsights();
    expect(notifications.error).toHaveBeenCalledWith('network.insights.loadError');
    await new Promise(resolve => setTimeout(resolve, 100));
    expect(impressions.seen).not.toHaveBeenCalled();
    fixture.destroy();

    fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, id: 9, own: false });
    fixture.detectChanges();
    element = fixture.nativeElement;
    document.body.appendChild(element);
    expect(element.querySelector('[data-post-insights]')).toBeNull();
    await new Promise(resolve => setTimeout(resolve, 200));
    expect(impressions.seen).toHaveBeenCalledOnceWith(9);
    element.remove();
    fixture.destroy();
  });

  it('likes the post, or no longer, and lists who likes it', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    network.likers.and.returnValue(of([post.author]));
    (element.querySelector('[data-likes]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-likers] a')?.getAttribute('href')).toBe('/m/camille');
    network.react.and.returnValue(of({ ...post, likes: 3, liked: true, myReaction: 'LIKE' as const,
      reactions: [{ reaction: 'BRAVO' as const, count: 2 }, { reaction: 'LIKE' as const, count: 1 }] }));
    (element.querySelector('[data-like]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.react).toHaveBeenCalledWith(4, 'LIKE');
    expect(network.likers).toHaveBeenCalledTimes(3);
    expect(element.querySelector('[data-like]')?.getAttribute('aria-pressed')).toBe('true');
    expect(element.querySelector('[data-likes] .emojis')?.textContent).toBe('👏❤️');
    (element.querySelector('[data-likes]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-likers]')).toBeNull();
    network.react.and.returnValue(throwError(() => new Error('down')));
    (element.querySelector('[data-like]') as HTMLButtonElement).click();
    expect(component.post.myReaction).toBe('LIKE');
    expect(component.topReactions()).toEqual(['👏', '❤️']);
    component.post = { ...component.post, reactions: [] };
    expect(component.topReactions()).toEqual(['❤️']);
    network.likers.and.returnValue(throwError(() => new Error('down')));
    component.showLikers();
    expect(notifications.error).toHaveBeenCalledWith('network.post.likeError');
    expect(notifications.error).toHaveBeenCalledTimes(2);
  });

  it('opens the comments under the post, and lets its author close them', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-comment-count]')?.textContent).toContain('network.post.comments');
    expect(element.querySelector('[data-show-comments]')?.textContent).toContain('network.post.comment');
    (element.querySelector('[data-show-comments]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('app-post-comments')).not.toBeNull();
    expect(component.post.comments).toBe(3);
    network.closeComments.and.returnValue(of(undefined));
    component.toggleClosed();
    expect(network.closeComments).toHaveBeenCalledWith(4, true);
    expect(component.post.commentsClosed).toBeTrue();
    network.closeComments.and.returnValue(throwError(() => new Error('down')));
    component.toggleClosed();
    expect(notifications.error).toHaveBeenCalledWith('network.post.commentError');
  });

  it('lets its author edit it for 15 minutes, then shows its earlier texts', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, createdAt: new Date(Date.now() - 60_000).toISOString() });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(component.editable).toBeTrue();
    component.startEditing();
    fixture.detectChanges();
    expect((element.querySelector('[data-post-edit-text]') as HTMLTextAreaElement).value).toContain('Merci');
    component.editing!.setValue('  ');
    component.saveEdit();
    expect(network.editPost).not.toHaveBeenCalled();
    network.editPost.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.editing!.setValue('Revu');
    component.saveEdit();
    expect(notifications.error).toHaveBeenCalledWith('network.post.editTooLate');
    network.editPost.and.returnValue(of({ ...post, text: 'Revu', edited: true }));
    component.saveEdit();
    fixture.detectChanges();
    expect(component.editing).toBeNull();
    expect(element.querySelector('.text')?.textContent).toContain('Revu');
    network.postVersions.and.returnValue(of([{ text: 'Merci', replacedAt: '2026-11-14T10:05:00' }]));
    (element.querySelector('[data-post-edited]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-post-versions]')?.textContent).toContain('Merci');
    component.toggleVersions();
    expect(component.versions).toBeNull();
    network.postVersions.and.returnValue(throwError(() => new Error('down')));
    component.toggleVersions();
    network.editPost.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.startEditing();
    component.saveEdit();
    expect(notifications.error).toHaveBeenCalledWith('network.post.editError');
    fixture.componentRef.setInput('post', { ...post, createdAt: '2020-01-01T00:00:00' });
    expect(component.editable).toBeFalse();
  });

  it('saves the post to the reader\'s list, or takes it off, telling who shows it', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const unsaved = jasmine.createSpy('unsaved');
    fixture.componentInstance.unsaved.subscribe(unsaved);
    network.bookmark.and.returnValue(of({ ...post, bookmarked: true }));
    (fixture.nativeElement.querySelector('[data-bookmark]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.bookmark).toHaveBeenCalledWith(4, true);
    expect(unsaved).toHaveBeenCalledWith(jasmine.objectContaining({ bookmarked: true }));
    expect(fixture.nativeElement.querySelector('[data-bookmark]').getAttribute('aria-pressed')).toBe('true');
    network.bookmark.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggleBookmark();
    expect(notifications.error).toHaveBeenCalledWith('network.post.likeError');
  });

  it('reposts with a comment, shows the original or that it is gone, and turns reposts off', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, repostOf: { id: 1, author: post.author, text: 'Original', createdAt: '2026-11-13T10:00:00',
      available: true } });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-original="1"]')?.textContent).toContain('Original');
    const reposted = jasmine.createSpy('reposted');
    component.reposted.subscribe(reposted);
    (element.querySelector('[data-repost]') as HTMLButtonElement).click();
    fixture.detectChanges();
    component.reposting!.setValue(' À lire ');
    network.repost.and.returnValue(of({ ...post, id: 8 }));
    component.repost();
    expect(network.repost).toHaveBeenCalledWith(4, 'À lire');
    expect(reposted).toHaveBeenCalledWith(jasmine.objectContaining({ id: 8 }));
    expect(component.post.reposts).toBe(1);
    expect(component.reposting).toBeNull();
    for (const [status, key] of [[409, 'network.post.repostsOffError'], [429, 'network.feed.tooManyPosts'], [500, 'network.post.editError']] as const) {
      network.repost.and.returnValue(throwError(() => new HttpErrorResponse({ status })));
      component.repost();
      expect(notifications.error).toHaveBeenCalledWith(key);
    }
    network.repostsOff.and.returnValue(of({ ...post, repostsOff: true }));
    component.toggleRepostsOff();
    fixture.detectChanges();
    expect(network.repostsOff).toHaveBeenCalledWith(4, true);
    expect(element.querySelector('[data-repost]')).toBeNull();
    network.repostsOff.and.returnValue(throwError(() => new Error('down')));
    component.toggleRepostsOff();
    fixture.componentRef.setInput('post', { ...post, repostOf: { id: 1, author: null, text: null, createdAt: null, available: false } });
    fixture.detectChanges();
    expect(element.querySelector('[data-original-gone]')).not.toBeNull();
  });

  it('links the author and the members mentioned, shows the text as text, and lets its author delete it', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    const deleted = jasmine.createSpy('deleted');
    fixture.componentInstance.deleted.subscribe(deleted);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.author')?.getAttribute('href')).toBe('/m/camille');
    expect(element.querySelector('.mention')?.getAttribute('href')).toBe('/m/leo_m');
    expect(element.querySelector('.tag')?.textContent).toBe('#Kafka');
    expect(element.querySelector('.tag')?.getAttribute('href')).toBe('/network/tags/kafka');
    expect(element.querySelector('.text b')).toBeNull();
    expect(element.querySelector('.text')?.textContent).toContain('<b>pas du HTML</b>');
    expect(element.querySelector('[data-post-actions]')).not.toBeNull();
    fixture.componentInstance.deleted.emit(post);
    expect(deleted).toHaveBeenCalledWith(post);
    fixture.componentRef.setInput('post', { ...post, own: false });
    fixture.detectChanges();
    expect(element.querySelector('[data-post-actions]')).not.toBeNull();
  });

  it('shows the card of its first link, opening the page apart', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, preview: { url: 'https://blog.example.org/kafka', site: 'Le blog', title: 'Kafka en prod',
      description: 'Un retour' } });
    fixture.detectChanges();
    const card = (fixture.nativeElement as HTMLElement).querySelector('[data-post-preview]') as HTMLAnchorElement;
    expect(card.href).toBe('https://blog.example.org/kafka');
    expect(card.rel).toContain('noopener');
    expect(card.target).toBe('_blank');
    expect(card.textContent).toContain('Kafka en prod');
    expect(card.textContent).toContain('Un retour');
    fixture.componentRef.setInput('post', { ...post, preview: { url: 'https://blog.example.org/a', site: null, title: 'A', description: null } });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.link-description')).toBeNull();
  });

  it('reports another member\'s post to the moderators', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, own: false });
    fixture.detectChanges();
    const open = spyOn(ReportDialogComponent, 'open').and.returnValue(of(true));
    fixture.componentInstance.report();
    expect(open).toHaveBeenCalledWith(jasmine.anything(), { target: 'posts', id: 4 });
  });

  it('copies the address of the post\'s own page, or says it could not', async () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const write = spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    fixture.componentInstance.copyLink();
    await fixture.whenStable();
    expect(write).toHaveBeenCalledWith(`${window.location.origin}/network/posts/4`);
    expect(notifications.success).toHaveBeenCalledWith('network.post.linkCopied');
    write.and.returnValue(Promise.reject(new Error('denied')));
    fixture.componentInstance.copyLink();
    await fixture.whenStable();
    await Promise.resolve();
    expect(notifications.error).toHaveBeenCalledWith('network.post.linkCopyError');
  });

  it('shows a mission\'s kind and facts, and a question as answered', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    const details = { role: 'Lead Java', rateMin: 600, rateMax: 700, city: 'Lyon', remoteDays: 2, start: '2027-01-04', client: 'Acme',
      stack: ['Java', 'Kafka'] };
    fixture.componentRef.setInput('post', { ...post, kind: 'MISSION', details });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-post-kind-badge="MISSION"]')).not.toBeNull();
    const facts = element.querySelector('[data-post-facts]')!;
    expect(facts.textContent).toContain('Lead Java');
    expect(facts.querySelector('[data-fact-rate]')?.textContent).toContain('600–700 €');
    expect(facts.textContent).toContain('Acme');
    expect(facts.textContent).toContain('Java, Kafka');
    const component = fixture.componentInstance;
    expect(component.rate({ ...details, rateMax: 600, rateMin: 600 })).toBe('600 €');
    expect(component.rate({ ...details, rateMax: null })).toBe('network.kinds.rateFrom');
    expect(component.rate({ ...details, rateMin: null })).toBe('network.kinds.rateUpTo');
    fixture.componentRef.setInput('post', { ...post, kind: 'QUESTION', answerCommentId: 3 });
    fixture.detectChanges();
    expect(element.querySelector('[data-post-answered]')).not.toBeNull();
    expect(element.querySelector('[data-post-facts]')).toBeNull();
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    expect(element.querySelector('[data-post-kind-badge]')).toBeNull();
  });

  it('lets the reader vote in a poll, then shows the shares, the reader\'s choice and when it closes', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    const open = { choices: [{ text: 'IntelliJ', votes: null }, { text: 'Vim', votes: null }], endsAt: '2026-11-18T10:00:00Z',
      closed: false, myChoice: null, total: null };
    fixture.componentRef.setInput('post', { ...post, own: false, kind: 'POLL', poll: open });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-poll-vote]').length).toBe(2);
    const counted = { ...open, choices: [{ text: 'IntelliJ', votes: 1 }, { text: 'Vim', votes: 2 }], myChoice: 1, total: 3 };
    network.vote.and.returnValue(of({ ...post, own: false, kind: 'POLL', poll: counted }));
    (element.querySelector('[data-poll-vote="1"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.vote).toHaveBeenCalledWith(4, 1);
    expect(element.querySelector('[data-poll-result="1"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(element.querySelector('[data-poll-result="1"]')?.textContent).toContain('67 %');
    expect(fixture.componentInstance.share({ ...counted, total: 0 }, 0)).toBe(0);
    network.vote.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.vote(0);
    expect(notifications.error).toHaveBeenCalledWith('network.poll.voteError');
    expect(fixture.componentInstance.isVoting).toBeFalse();
    fixture.componentRef.setInput('post', { ...post, kind: 'POLL', poll: { ...counted, closed: true } });
    fixture.detectChanges();
    expect(element.querySelector('[data-poll-closed]')).not.toBeNull();
    expect((element.querySelector('[data-poll-result="0"]') as HTMLButtonElement).disabled).toBeTrue();
  });

});
