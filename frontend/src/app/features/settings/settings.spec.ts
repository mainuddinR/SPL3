import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
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
    localStorage.removeItem('spl3.theme'); localStorage.removeItem('spl3.page-size');
    response = new Subject();
    service = jasmine.createSpyObj<SettingsApiService>('SettingsApiService', ['status']);
    service.status.and.returnValue(response.asObservable());
    auth = jasmine.createSpyObj<Auth>('Auth', ['logout']);
    await TestBed.configureTestingModule({ imports: [Settings], providers: [
      provideRouter([]), { provide: SettingsApiService, useValue: service }, { provide: Auth, useValue: auth }
    ] }).compileComponents();
  });
  afterEach(() => { localStorage.removeItem('spl3.theme'); localStorage.removeItem('spl3.page-size');
    document.documentElement.classList.remove('dark'); });

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
    expect(localStorage.getItem('spl3.theme')).toBe('dark');
    expect(localStorage.getItem('spl3.page-size')).toBe('25');
    expect(document.documentElement.classList.contains('dark')).toBeTrue();
    const reloaded = new UiPreferencesService();
    expect(reloaded.theme()).toBe('dark');
    expect(reloaded.pageSize()).toBe(25);
    component.reset();
    expect(localStorage.getItem('spl3.theme')).toBeNull();
    expect(localStorage.getItem('spl3.page-size')).toBeNull();
    const router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    component.signOut();
    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
