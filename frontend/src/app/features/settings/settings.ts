import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Auth } from '../../core/auth/auth';
import { SettingsApiService } from '../../services/settings-api.service';
import { UiPreferencesService, ThemePreference, PageSizePreference } from '../../services/ui-preferences.service';
import { UiStatus } from '../../models/settings.model';

@Component({ selector: 'app-settings', standalone: true, imports: [CommonModule], templateUrl: './settings.html' })
export class Settings implements OnInit {
  private api = inject(SettingsApiService);
  private auth = inject(Auth);
  private router = inject(Router);
  preferences = inject(UiPreferencesService);
  status = signal<UiStatus | null>(null);
  loading = signal(true);
  error = signal(false);

  ngOnInit() { this.refresh(); }
  refresh() {
    this.loading.set(true); this.error.set(false);
    this.api.status().subscribe({
      next: value => { this.status.set(value); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }
  changeTheme(event: Event) { this.preferences.setTheme((event.target as HTMLSelectElement).value as ThemePreference); }
  changePageSize(event: Event) { this.preferences.setPageSize(Number((event.target as HTMLSelectElement).value) as PageSizePreference); }
  reset() { this.preferences.reset(); }
  signOut() { this.auth.logout(); this.router.navigateByUrl('/login'); }
}
