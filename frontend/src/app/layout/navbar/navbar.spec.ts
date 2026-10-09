import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { NotificationService } from '../../services/notification.service';
import { SidebarService } from '../sidebar.service';
import { Navbar } from './navbar';

@Component({ standalone: true, template: '<p>Settings page</p>' })
class DummySettings {}

describe('Navbar profile menu', () => {
  it('navigates to the actual Settings route and closes the profile menu', async () => {
    const notifications = {
      unreadCount: signal(0), monitorStatus: signal(null), notifications: signal([]),
      unreadOnly: signal(false), loading: signal(false), error: signal(false),
      refresh: jasmine.createSpy('refresh'), clear: jasmine.createSpy('clear'),
      setFilter: jasmine.createSpy('setFilter'), markRead: jasmine.createSpy('markRead'),
      markAllRead: jasmine.createSpy('markAllRead')
    };
    TestBed.configureTestingModule({
      imports: [Navbar],
      providers: [
        provideRouter([{ path: 'settings', component: DummySettings }]),
        { provide: Auth, useValue: { currentUser$: of({ username: 'tester', avatarUrl: '' }), logout: jasmine.createSpy('logout') } },
        { provide: NotificationService, useValue: notifications },
        { provide: SidebarService, useValue: { toggle: jasmine.createSpy('toggle') } }
      ]
    });
    const fixture = TestBed.createComponent(Navbar);
    fixture.detectChanges();
    const userButton = fixture.nativeElement.querySelector('button[class*="space-x-3"]') as HTMLButtonElement;
    userButton.click(); fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/settings"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    link.click();
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/settings');
    expect(fixture.componentInstance.isProfileOpen()).toBeFalse();
  });
});
