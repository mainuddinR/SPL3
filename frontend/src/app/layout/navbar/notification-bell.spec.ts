import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { NotificationBell } from './notification-bell';
import { NotificationService } from '../../services/notification.service';

describe('NotificationBell', () => {
  const unreadCount = signal(2);
  const notifications = signal([{ id: 1, type: 'NEW_PR' as const, repository: 'owner/repo',
    pullRequestId: 19, pullRequestNumber: 7, title: 'New pull request #7', message: 'Change',
    read: false, createdAt: new Date().toISOString() }]);
  const loading = signal(false);
  const error = signal(false);
  const unreadOnly = signal(false);
  const monitorStatus = signal<{ enabled: boolean; githubSessionConnected: boolean } | null>(null);
  const api = { unreadCount, notifications, loading, error, unreadOnly, monitorStatus, refresh: jasmine.createSpy('refresh'),
    setFilter: jasmine.createSpy('setFilter').and.callFake((value: boolean) => unreadOnly.set(value)),
    markRead: jasmine.createSpy('markRead'), markAllRead: jasmine.createSpy('markAllRead') };

  beforeEach(async () => {
    unreadCount.set(2); unreadOnly.set(false); monitorStatus.set(null);
    notifications.set([{ id: 1, type: 'NEW_PR', repository: 'owner/repo', pullRequestId: 19,
      pullRequestNumber: 7, title: 'New pull request #7', message: 'Change', read: false,
      createdAt: new Date().toISOString() }]);
    api.refresh.calls.reset(); api.markRead.calls.reset(); api.markAllRead.calls.reset();
    await TestBed.configureTestingModule({ imports: [NotificationBell], providers: [
      provideRouter([]), { provide: NotificationService, useValue: api }
    ] }).compileComponents();
  });
  afterEach(() => document.documentElement.classList.remove('dark'));

  it('shows unread count and opens, filters, marks and closes the panel', () => {
    const fixture = TestBed.createComponent(NotificationBell); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('2');
    const component = fixture.componentInstance;
    component.toggle(new Event('click')); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('New pull request #7');
    expect(api.refresh).toHaveBeenCalled();
    component.notifications.setFilter(true); fixture.detectChanges();
    expect(unreadOnly()).toBeTrue();
    component.notifications.markAllRead();
    expect(api.markAllRead).toHaveBeenCalled();
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    component.navigate(1, 19);
    expect(api.markRead).toHaveBeenCalledWith(1);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard/pulls', 19]);
    component.toggle(new Event('click'));
    component.closeEscape();
    expect(component.open()).toBeFalse();
  });

  it('shows empty state and hides zero badge', () => {
    unreadCount.set(0); notifications.set([]);
    const fixture = TestBed.createComponent(NotificationBell);
    fixture.componentInstance.toggle(new Event('click')); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("You're all caught up.");
    expect(fixture.nativeElement.querySelector('[aria-label="Unread notifications"]')).toBeNull();
  });

  it('uses theme-aware All and Unread tabs without a white active fill', () => {
    const fixture = TestBed.createComponent(NotificationBell);
    fixture.componentInstance.toggle(new Event('click')); fixture.detectChanges();
    const tabs = fixture.nativeElement.querySelectorAll('[role="tab"]') as NodeListOf<HTMLButtonElement>;
    expect(tabs.length).toBe(2);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[1].getAttribute('aria-selected')).toBe('false');
    expect(tabs[0].classList.contains('bg-white')).toBeFalse();
    htmlTheme('dark');
    expect(getComputedStyle(tabs[0]).backgroundColor).not.toBe('rgb(255, 255, 255)');
    api.setFilter(true); fixture.detectChanges();
    expect(tabs[0].getAttribute('aria-selected')).toBe('false');
    expect(tabs[1].getAttribute('aria-selected')).toBe('true');
    htmlTheme('light');
    expect(getComputedStyle(tabs[1]).backgroundColor).not.toBe('rgb(255, 255, 255)');
    document.documentElement.classList.remove('dark');
  });

  it('keeps unread and read rows distinguishable by text and exposes reconnect safely', () => {
    monitorStatus.set({ enabled: true, githubSessionConnected: false });
    notifications.set([{ id: 1, type: 'NEW_PR', repository: 'owner/repo', pullRequestId: 19,
      pullRequestNumber: 7, title: 'New pull request #7', message: 'Change', read: false,
      createdAt: new Date().toISOString() },
      { id: 2, type: 'NEW_PR', repository: 'owner/repo', pullRequestId: 20,
        pullRequestNumber: 8, title: 'New pull request #8', message: 'Older', read: true,
        createdAt: new Date().toISOString() }]);
    const fixture = TestBed.createComponent(NotificationBell);
    fixture.componentInstance.toggle(new Event('click')); fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('.notification-row') as NodeListOf<HTMLElement>;
    expect(rows[0].classList.contains('notification-row-unread')).toBeTrue();
    expect(rows[0].textContent).toContain('Unread notification');
    expect(rows[1].classList.contains('notification-row-unread')).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('GitHub monitoring paused');
    expect(fixture.nativeElement.querySelector('a[href="http://localhost:8080/oauth2/authorization/github"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('private-token');
    expect(fixture.nativeElement.querySelector('[aria-label="Unread notifications"]')).not.toBeNull();
  });

  function htmlTheme(value: 'light' | 'dark') {
    document.documentElement.classList.toggle('dark', value === 'dark');
  }
});
