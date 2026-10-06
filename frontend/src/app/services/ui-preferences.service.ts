import { Injectable, signal } from '@angular/core';

export type ThemePreference = 'light' | 'dark' | 'system';
export type PageSizePreference = 10 | 25 | 50;

@Injectable({ providedIn: 'root' })
export class UiPreferencesService {
  private themeKey = 'spl3.theme';
  private pageSizeKey = 'spl3.page-size';
  theme = signal<ThemePreference>(this.readTheme());
  pageSize = signal<PageSizePreference>(this.readPageSize());
  private media = typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  constructor() {
    this.applyTheme();
    this.media?.addEventListener('change', () => {
      if (this.theme() === 'system') this.applyTheme();
    });
  }

  setTheme(value: ThemePreference) {
    this.theme.set(value);
    this.store(this.themeKey, value);
    this.applyTheme();
  }

  setPageSize(value: PageSizePreference) {
    this.pageSize.set(value);
    this.store(this.pageSizeKey, String(value));
  }

  reset() {
    this.remove(this.themeKey);
    this.remove(this.pageSizeKey);
    this.theme.set('system');
    this.pageSize.set(10);
    this.applyTheme();
  }

  private applyTheme() {
    const dark = this.theme() === 'dark' || (this.theme() === 'system' && !!this.media?.matches);
    if (typeof document !== 'undefined') document.documentElement.classList.toggle('dark', dark);
  }

  private readTheme(): ThemePreference {
    const value = this.read(this.themeKey);
    return value === 'light' || value === 'dark' ? value : 'system';
  }

  private readPageSize(): PageSizePreference {
    const value = Number(this.read(this.pageSizeKey));
    return value === 25 || value === 50 ? value : 10;
  }

  private read(key: string): string | null {
    try { return localStorage.getItem(key); } catch { return null; }
  }
  private store(key: string, value: string) {
    try { localStorage.setItem(key, value); } catch { /* UI remains usable without storage. */ }
  }
  private remove(key: string) {
    try { localStorage.removeItem(key); } catch { /* UI remains usable without storage. */ }
  }
}
