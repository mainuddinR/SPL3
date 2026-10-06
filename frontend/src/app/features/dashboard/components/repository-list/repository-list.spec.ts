import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { GithubService } from '../../../../services/github.service';
import { GithubRepo, RepositoryPage } from '../../../../models/github.model';
import { RepositoryList } from './repository-list';

describe('RepositoryList discovery', () => {
  let fixture: ComponentFixture<RepositoryList>;
  let component: RepositoryList;
  let service: jasmine.SpyObj<GithubService>;
  let firstPage: Subject<RepositoryPage>;
  let router: jasmine.SpyObj<Router>;
  const repo = (id: number, name: string): GithubRepo => ({ id, name, full_name: `owner/${name}`,
    html_url: `https://github.com/owner/${name}`, description: '', language: 'Java',
    stargazers_count: 0, owner: { login: 'owner', avatar_url: '' } });
  const page = (repositories: GithubRepo[], hasNext = false): RepositoryPage =>
    ({ repositories, storedRepositoryIds: [2], page: 1, hasNext });
  const text = () => fixture.nativeElement.textContent as string;

  beforeEach(async () => {
    firstPage = new Subject();
    service = jasmine.createSpyObj<GithubService>('GithubService', ['myRepositories', 'userRepositories']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    service.myRepositories.and.returnValue(firstPage.asObservable());
    service.userRepositories.and.returnValue(of(page([])));
    await TestBed.configureTestingModule({ imports: [RepositoryList], providers: [
      { provide: GithubService, useValue: service }, { provide: Router, useValue: router }
    ] }).compileComponents();
    fixture = TestBed.createComponent(RepositoryList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('loads My Repositories by default and marks saved repositories', () => {
    expect(service.myRepositories).toHaveBeenCalledOnceWith(1);
    expect(text()).toContain('Loading repositories');
    firstPage.next(page([repo(1, 'portfolio'), repo(2, 'saved')])); fixture.detectChanges();
    expect(text()).toContain('portfolio');
    expect(text()).toContain('Already stored');
    component.selectRepo(repo(2, 'saved'));
    expect(router.navigate).not.toHaveBeenCalled();
    component.selectRepo(repo(1, 'portfolio'));
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard/repos', 'owner', 'portfolio']);
  });

  it('debounces local filtering, handles backspace and clears to the full page', fakeAsync(() => {
    firstPage.next(page([repo(1, 'portfolio'), repo(3, 'other')])); fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('#repo-filter') as HTMLInputElement;
    input.value = 'portfolio'; input.dispatchEvent(new Event('input'));
    tick(399); expect(component.filtered().length).toBe(2);
    tick(1); expect(component.filtered().length).toBe(1);
    input.value = 'portfo'; input.dispatchEvent(new Event('input'));
    tick(400); expect(component.filtered()[0].name).toBe('portfolio');
    input.value = 'nonsense'; input.dispatchEvent(new Event('input'));
    tick(400); fixture.detectChanges();
    expect(text()).toContain('No repositories matched your search.');
    expect(text()).not.toContain('Failed to fetch repositories');
    input.value = ''; input.dispatchEvent(new Event('input'));
    expect(component.filtered().length).toBe(2);
    tick(400);
    expect(service.myRepositories).toHaveBeenCalledTimes(1);
  }));

  it('cancels an old page and only accepts the latest page response', () => {
    const newer = new Subject<RepositoryPage>();
    service.myRepositories.and.returnValue(newer.asObservable());
    component.loadPage(2);
    firstPage.next(page([repo(1, 'stale')]));
    expect(component.repositories()).toEqual([]);
    newer.next({ ...page([repo(3, 'current')]), page: 2 });
    expect(component.repositories()[0].name).toBe('current');
    expect(component.currentPage()).toBe(2);
  });

  it('resolves exact username on submit and filters only that user page', fakeAsync(() => {
    component.setMode('user'); fixture.detectChanges();
    expect(service.userRepositories).not.toHaveBeenCalled();
    const username = fixture.nativeElement.querySelector('#github-username') as HTMLInputElement;
    username.value = 'mainuddinR'; username.dispatchEvent(new Event('input'));
    expect(service.userRepositories).not.toHaveBeenCalled();
    service.userRepositories.and.returnValue(of(page([repo(4, 'portfolio-lab'), repo(5, 'other')])));
    component.findUser(); fixture.detectChanges();
    expect(service.userRepositories).toHaveBeenCalledOnceWith('mainuddinR', 1);
    const filter = fixture.nativeElement.querySelector('#repo-filter') as HTMLInputElement;
    filter.value = 'portfolio'; filter.dispatchEvent(new Event('input'));
    tick(400);
    expect(component.filtered().map(item => item.name)).toEqual(['portfolio-lab']);
  }));

  it('shows safe rate-limit and re-login messages', () => {
    firstPage.error(new HttpErrorResponse({ status: 429, error: { message: 'provider details' } }));
    fixture.detectChanges();
    expect(text()).toContain('GitHub request limit reached');
    expect(text()).not.toContain('provider details');
    service.myRepositories.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    component.loadPage(1); fixture.detectChanges();
    expect(text()).toContain('Sign in with GitHub again');
  });
});
