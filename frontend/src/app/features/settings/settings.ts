import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Auth } from '../../core/auth/auth';
import { SettingsApiService } from '../../services/settings-api.service';
import { UiPreferencesService, ThemePreference, PageSizePreference } from '../../services/ui-preferences.service';
import { GithubMonitorStatus, UiStatus } from '../../models/settings.model';

@Component({ selector: 'app-settings', standalone: true, imports: [CommonModule], templateUrl: './settings.html' })
export class Settings implements OnInit {
  private api = inject(SettingsApiService);
  private auth = inject(Auth);
  private router = inject(Router);
  preferences = inject(UiPreferencesService);
  status = signal<UiStatus | null>(null);
  monitorStatus = signal<GithubMonitorStatus | null>(null);
  loading = signal(true);
  error = signal(false);
  lastChecked = signal<Date | null>(null);

  ngOnInit() { this.refresh(); }
  refresh() {
    this.loading.set(true); this.error.set(false);
    this.api.githubMonitorStatus().subscribe({
      next: value => this.monitorStatus.set(value),
      error: () => this.monitorStatus.set(null)
    });
    this.api.status().subscribe({
      next: value => { this.status.set(value); this.lastChecked.set(new Date()); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }
  changeTheme(event: Event) { this.preferences.setTheme((event.target as HTMLSelectElement).value as ThemePreference); }
  changePageSize(event: Event) { this.preferences.setPageSize(Number((event.target as HTMLSelectElement).value) as PageSizePreference); }
  changeDensity(event: Event) { this.preferences.setDensity((event.target as HTMLSelectElement).value as 'comfortable' | 'compact'); }
  changeSidebar(event: Event) { this.preferences.setSidebar((event.target as HTMLSelectElement).value as 'expanded' | 'compact'); }
  changeMotion(event: Event) { this.preferences.setMotion((event.target as HTMLSelectElement).value as 'normal' | 'reduced'); }
  changeEffects(event: Event) { this.preferences.setEffects((event.target as HTMLSelectElement).value as 'standard' | 'reduced'); }
  changeNotificationView(event: Event) { this.preferences.setNotificationDefaultView((event.target as HTMLSelectElement).value as 'all' | 'unread'); }
  changeNotificationAutoRefresh(event: Event) { this.preferences.setNotificationAutoRefresh((event.target as HTMLInputElement).checked); }
  reset() { this.preferences.reset(); }
  signOut() { this.auth.logout(); this.router.navigateByUrl('/login'); }
}
