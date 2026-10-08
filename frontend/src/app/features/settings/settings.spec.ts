import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { Auth } from '../../core/auth/auth';
import { SettingsApiService } from '../../services/settings-api.service';
import { UiPreferencesService } from '../../services/ui-preferences.service';
import { UiStatus } from '../../models/settings.model';
import { routes } from '../../app.routes';
import { Settings } from './settings';

describe('Settings', () => {
  const status: UiStatus = { githubId: '123', username: 'developer', avatarUrl: null,
    connectedRepositoryCount: 2, githubSessionConnected: true, mlServiceAvailable: true,
    databaseConnected: true, ai: { llmEnabled: false, apiKeyConfigured: false, model: 'gpt-5-mini' } };
  let service: jasmine.SpyObj<SettingsApiService>;
  let auth: jasmine.SpyObj<Auth>;
  let response: Subject<UiStatus>;

  beforeEach(async () => {
    for (const key of ['theme', 'page-size', 'density', 'sidebar', 'motion', 'effects', 'tooltips']) localStorage.removeItem(`spl3.${key}`);
    response = new Subject();
    service = jasmine.createSpyObj<SettingsApiService>('SettingsApiService', ['status', 'githubMonitorStatus']);
    service.status.and.returnValue(response.asObservable());
    service.githubMonitorStatus.and.returnValue(of({ enabled: true, githubSessionConnected: true }));
    auth = jasmine.createSpyObj<Auth>('Auth', ['logout']);
    await TestBed.configureTestingModule({ imports: [Settings], providers: [
      provideRouter([]), { provide: SettingsApiService, useValue: service }, { provide: Auth, useValue: auth }
    ] }).compileComponents();
  });
  afterEach(() => { for (const key of ['theme', 'page-size', 'density', 'sidebar', 'motion', 'effects', 'tooltips']) localStorage.removeItem(`spl3.${key}`);
    document.documentElement.classList.remove('dark', 'density-compact', 'sidebar-compact', 'motion-reduced', 'effects-reduced', 'tooltips-disabled'); });

  it('has a real route and renders safe account, AI, integration, and privacy status', () => {
    expect(routes.find(route => route.path === '')?.children?.some(route => route.path === 'settings')).toBeTrue();
    const fixture = TestBed.createComponent(Settings); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Settings');
    response.next(status); fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('developer');
    expect(text).toContain('Connected repositories: 2');
    expect(text).toContain('Disabled by server');
    expect(text).toContain('Internal ML endpoint');
    expect(text).toContain('Privacy & Processing');
    expect(text).not.toContain('OPENAI_API_KEY');
  });

  it('persists theme and page size, resets only UI preferences, and uses existing logout', () => {
    const fixture = TestBed.createComponent(Settings); fixture.detectChanges();
    response.next(status); fixture.detectChanges();
    const component = fixture.componentInstance;
    component.changeTheme({ target: { value: 'dark' } } as unknown as Event);
    component.changePageSize({ target: { value: '25' } } as unknown as Event);
    component.changeDensity({ target: { value: 'compact' } } as unknown as Event);
    component.changeMotion({ target: { value: 'reduced' } } as unknown as Event);
    expect(localStorage.getItem('spl3.theme')).toBe('dark');
    expect(localStorage.getItem('spl3.page-size')).toBe('25');
    expect(document.documentElement.classList.contains('dark')).toBeTrue();
    const reloaded = new UiPreferencesService();
    expect(reloaded.theme()).toBe('dark');
    expect(reloaded.pageSize()).toBe(25);
    expect(reloaded.density()).toBe('compact');
    expect(reloaded.motion()).toBe('reduced');
    expect(document.documentElement.classList.contains('motion-reduced')).toBeTrue();
    component.reset();
    expect(localStorage.getItem('spl3.theme')).toBeNull();
    expect(localStorage.getItem('spl3.page-size')).toBeNull();
    expect(localStorage.getItem('spl3.density')).toBeNull();
    const router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    component.signOut();
    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('shows reconnect guidance only when enabled monitoring lacks a GitHub session', () => {
    service.githubMonitorStatus.and.returnValue(of({ enabled: true, githubSessionConnected: false }));
    const fixture = TestBed.createComponent(Settings); fixture.detectChanges();
    response.next({ ...status, githubSessionConnected: false }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('GitHub monitoring paused');
    expect(fixture.nativeElement.querySelector('a[href="http://localhost:8080/oauth2/authorization/github"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('private-token');
  });

  it('preserves Light, Dark, and System theme choices', () => {
    const preferences = TestBed.inject(UiPreferencesService);
    preferences.setTheme('light');
    expect(document.documentElement.classList.contains('dark')).toBeFalse();
    preferences.setTheme('dark');
    expect(document.documentElement.classList.contains('dark')).toBeTrue();
    preferences.setTheme('system');
    expect(preferences.theme()).toBe('system');
    expect(localStorage.getItem('spl3.theme')).toBe('system');
    expect(document.documentElement.classList.contains('dark')).toBe(window.matchMedia('(prefers-color-scheme: dark)').matches);
  });
});
