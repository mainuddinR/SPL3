import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NotificationPage, PrNotification } from '../models/notification.model';
import { Auth } from '../core/auth/auth';
import { SettingsApiService } from './settings-api.service';
import { GithubMonitorStatus } from '../models/settings.model';
import { catchError, EMPTY, interval, switchMap } from 'rxjs';

const URL = 'http://localhost:8080/api/notifications';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  private auth = inject(Auth);
  private settings = inject(SettingsApiService);
  monitorStatus = signal<GithubMonitorStatus | null>(null);
  unreadCount = signal(0);
  notifications = signal<PrNotification[]>([]);
  loading = signal(false);
  error = signal(false);
  unreadOnly = signal(false);

  constructor() {
    this.auth.currentUser$.subscribe(user => {
      if (user) this.refresh(); else this.clear();
    });
    interval(45000).pipe(switchMap(() => {
      if (!this.auth.isLoggedIn() || (typeof document !== 'undefined' && document.hidden)) return EMPTY;
      this.refreshMonitorStatus();
      return this.http.get<{ count: number }>(`${URL}/unread-count`).pipe(catchError(() => EMPTY));
    })).subscribe(result => { this.unreadCount.set(result.count); this.load(); });
  }

  refresh() {
    if (!this.auth.isLoggedIn()) return;
    this.http.get<{ count: number }>(`${URL}/unread-count`).subscribe({
      next: result => this.unreadCount.set(result.count), error: () => this.error.set(true)
    });
    this.load();
    this.refreshMonitorStatus();
  }

  refreshMonitorStatus() {
    if (!this.auth.isLoggedIn()) return;
    this.settings.githubMonitorStatus().subscribe({
      next: status => this.monitorStatus.set(status),
      error: () => this.monitorStatus.set(null)
    });
  }

  load() {
    if (!this.auth.isLoggedIn()) return;
    this.loading.set(true);
    this.error.set(false);
    this.http.get<NotificationPage>(URL, { params: { page: 0, size: 20, unreadOnly: this.unreadOnly() } }).subscribe({
      next: page => { this.notifications.set(page.content); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }

  setFilter(unreadOnly: boolean) { this.unreadOnly.set(unreadOnly); this.load(); }
  markRead(id: number, after?: () => void) {
    this.http.post<PrNotification>(`${URL}/${id}/read`, {}).subscribe({
      next: () => { this.refresh(); after?.(); }, error: () => this.error.set(true)
    });
  }
  markAllRead() {
    this.http.post<void>(`${URL}/read-all`, {}).subscribe({
      next: () => this.refresh(), error: () => this.error.set(true)
    });
  }
  clear() { this.unreadCount.set(0); this.notifications.set([]); this.monitorStatus.set(null); }
}
