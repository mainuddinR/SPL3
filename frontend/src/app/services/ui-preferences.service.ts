import { Injectable, signal } from '@angular/core';

export type ThemePreference = 'light' | 'dark' | 'system';
export type PageSizePreference = 10 | 25 | 50;
export type DensityPreference = 'comfortable' | 'compact';
export type SidebarPreference = 'expanded' | 'compact';
export type MotionPreference = 'normal' | 'reduced';
export type EffectsPreference = 'standard' | 'reduced';

@Injectable({ providedIn: 'root' })
export class UiPreferencesService {
  private themeKey = 'spl3.theme';
  private pageSizeKey = 'spl3.page-size';
  theme = signal<ThemePreference>(this.readTheme());
  pageSize = signal<PageSizePreference>(this.readPageSize());
  density = signal<DensityPreference>(this.choice('spl3.density', 'comfortable', 'compact'));
  sidebar = signal<SidebarPreference>(this.choice('spl3.sidebar', 'expanded', 'compact'));
  motion = signal<MotionPreference>(this.choice('spl3.motion', 'normal', 'reduced'));
  effects = signal<EffectsPreference>(this.choice('spl3.effects', 'standard', 'reduced'));
  private media = typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  constructor() {
    this.applyTheme();
    this.applyAppearance();
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
  setDensity(value: DensityPreference) { this.density.set(value); this.store('spl3.density', value); this.applyAppearance(); }
  setSidebar(value: SidebarPreference) { this.sidebar.set(value); this.store('spl3.sidebar', value); this.applyAppearance(); }
  setMotion(value: MotionPreference) { this.motion.set(value); this.store('spl3.motion', value); this.applyAppearance(); }
  setEffects(value: EffectsPreference) { this.effects.set(value); this.store('spl3.effects', value); this.applyAppearance(); }

  reset() {
    this.remove(this.themeKey);
    this.remove(this.pageSizeKey);
    for (const key of ['spl3.density', 'spl3.sidebar', 'spl3.motion', 'spl3.effects']) this.remove(key);
    this.theme.set('system');
    this.pageSize.set(10);
    this.density.set('comfortable'); this.sidebar.set('expanded'); this.motion.set('normal');
    this.effects.set('standard');
    this.applyTheme();
    this.applyAppearance();
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
  private choice<T extends string>(key: string, defaultValue: T, alternative: T): T {
    return this.read(key) === alternative ? alternative : defaultValue;
  }
  private applyAppearance() {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    root.classList.toggle('density-compact', this.density() === 'compact');
    root.classList.toggle('sidebar-compact', this.sidebar() === 'compact');
    root.classList.toggle('motion-reduced', this.motion() === 'reduced');
    root.classList.toggle('effects-reduced', this.effects() === 'reduced');
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
