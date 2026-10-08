import { Component, computed, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Subject, Subscription, debounceTime, distinctUntilChanged, switchMap, catchError, of, map } from 'rxjs';
import { GithubService } from '../../../../services/github.service';
import { GithubRepo, GithubUserResult, GithubUserSearchPage, RepositoryPage } from '../../../../models/github.model';
import { RepositoryCard } from '../repository-card/repository-card';
import { RemoveRepository } from '../repo-summary/remove-repository';

type Mode = 'mine' | 'user' | 'repository';
type LoadRequest = { mode: Mode; username: string; query: string; page: number };

@Component({ selector: 'app-repository-list', standalone: true, imports: [CommonModule, RepositoryCard, RemoveRepository],
  templateUrl: './repository-list.html' })
export class RepositoryList implements OnInit, OnDestroy {
  private githubService = inject(GithubService);
  private router = inject(Router);
  private filterInput = new Subject<string>();
  private loadRequests = new Subject<LoadRequest | null>();
  private userSearchRequests = new Subject<{ query: string; page: number } | null>();
  private subscriptions = new Subscription();

  mode = signal<Mode>('mine');
  repositories = signal<GithubRepo[]>([]);
  storedIds = signal<Set<number>>(new Set());
  filter = signal('');
  filterPending = signal(false);
  filtered = computed(() => {
    const query = this.filter().toLocaleLowerCase();
    return this.repositories().filter(repo => repo.name.toLocaleLowerCase().includes(query));
  });
  usernameInput = signal('');
  selectedUsername = signal('');
  userResults = signal<GithubUserResult[]>([]);
  submittedUserQuery = signal('');
  userSearchLoading = signal(false);
  userPage = signal(1);
  userHasNext = signal(false);
  repositoryInput = signal('');
  submittedRepositoryQuery = signal('');
  currentPage = signal(1);
  hasNext = signal(false);
  loading = signal(false);
  error = signal<string | null>(null);
  storedProjects = signal<Record<number, number>>({});
  removeTarget = signal<GithubRepo | null>(null);
  notice = signal('');

  openStored(repo: GithubRepo, view: 'pulls' | 'design-patterns') {
    const id = this.storedProjects()[repo.id];
    if (id) this.router.navigate(['/dashboard/projects', id, view]);
  }
  removedStored() {
    const repo = this.removeTarget();
    if (!repo) return;
    this.notice.set(`${repo.full_name} removed.`);
    this.storedIds.update(ids => new Set([...ids].filter(id => id !== repo.id)));
    this.storedProjects.update(ids => { const copy = { ...ids }; delete copy[repo.id]; return copy; });
    this.removeTarget.set(null);
  }

  ngOnInit() {
    this.subscriptions.add(this.githubService.storedProjects().subscribe({ next: projects => {
      this.storedProjects.set(Object.fromEntries(projects.map(p => [p.githubRepoId, p.id])));
    }, error: () => this.error.set('Saved repository actions are unavailable. Please refresh.') }));
    this.subscriptions.add(this.filterInput.pipe(debounceTime(400), distinctUntilChanged())
      .subscribe(value => { this.filter.set(value); this.filterPending.set(false); }));
    this.subscriptions.add(this.loadRequests.pipe(switchMap(request => {
      if (!request) return EMPTY;
      const response = request.mode === 'mine' ? this.githubService.myRepositories(request.page)
        : request.mode === 'user' ? this.githubService.userRepositories(request.username, request.page)
        : this.githubService.searchRepositories(request.query, request.page);
      return response.pipe(map(page => ({ page, error: null as HttpErrorResponse | null })),
        catchError(error => of({ page: null as RepositoryPage | null, error: error as HttpErrorResponse })));
    })).subscribe(result => {
      this.loading.set(false);
      if (result.error) {
        this.repositories.set([]); this.hasNext.set(false); this.error.set(this.message(result.error)); return;
      }
      this.repositories.set(result.page!.repositories);
      this.storedIds.set(new Set(result.page!.storedRepositoryIds));
      this.hasNext.set(result.page!.hasNext);
      this.currentPage.set(result.page!.page);
      this.error.set(null);
    }));
    this.subscriptions.add(this.userSearchRequests.pipe(switchMap(request => {
      if (!request) return EMPTY;
      return this.githubService.searchUsers(request.query, request.page).pipe(
        map(page => ({ page, error: null as HttpErrorResponse | null })),
        catchError(error => of({ page: null as GithubUserSearchPage | null, error: error as HttpErrorResponse })));
    })).subscribe(result => {
      this.userSearchLoading.set(false);
      if (result.error) {
        this.userResults.set([]); this.userHasNext.set(false); this.error.set(this.message(result.error)); return;
      }
      this.userResults.set(result.page!.users);
      this.userPage.set(result.page!.page);
      this.userHasNext.set(result.page!.hasNext);
      this.error.set(null);
    }));
    this.loadPage(1);
  }

  ngOnDestroy() { this.subscriptions.unsubscribe(); }

  setMode(mode: Mode) {
    if (this.mode() === mode) return;
    this.loadRequests.next(null); this.userSearchRequests.next(null);
    this.mode.set(mode);
    this.repositories.set([]); this.userResults.set([]);
    this.filter.set(''); this.filterInput.next(''); this.filterPending.set(false);
    this.error.set(null); this.loading.set(false); this.userSearchLoading.set(false);
    this.currentPage.set(1); this.hasNext.set(false);
    this.selectedUsername.set(''); this.submittedUserQuery.set(''); this.submittedRepositoryQuery.set('');
    if (mode === 'mine') this.loadPage(1);
  }

  onFilterInput(event: Event) {
    const value = (event.target as HTMLInputElement).value.trim();
    this.filterPending.set(true);
    if (!value) { this.filter.set(''); this.filterPending.set(false); }
    this.filterInput.next(value);
  }
  onUsernameInput(event: Event) { this.usernameInput.set((event.target as HTMLInputElement).value); }
  onRepositoryInput(event: Event) { this.repositoryInput.set((event.target as HTMLInputElement).value); }
  clearUserQuery() { this.usernameInput.set(''); }
  clearRepositoryQuery() { this.repositoryInput.set(''); }

  findUser() {
    if (this.userSearchLoading()) return;
    const query = this.usernameInput().trim();
    if (query.length < 3) { this.error.set('Enter at least 3 search characters.'); return; }
    this.error.set(null); this.userResults.set([]); this.userSearchLoading.set(true);
    this.submittedUserQuery.set(query); this.userPage.set(1);
    this.userSearchRequests.next({ query, page: 1 });
  }
  loadUserPage(page: number) {
    if (page < 1 || this.userSearchLoading() || !this.submittedUserQuery()) return;
    this.error.set(null); this.userSearchLoading.set(true);
    this.userSearchRequests.next({ query: this.submittedUserQuery(), page });
  }
  selectUser(user: GithubUserResult) {
    this.userSearchRequests.next(null); this.userSearchLoading.set(false);
    this.selectedUsername.set(user.login);
    this.repositories.set([]); this.filter.set(''); this.filterInput.next('');
    this.loadPage(1);
  }
  changeUser() {
    this.loadRequests.next(null); this.loading.set(false);
    this.selectedUsername.set(''); this.repositories.set([]);
    this.filter.set(''); this.filterInput.next(''); this.error.set(null);
  }
  searchRepository() {
    if (this.loading()) return;
    const query = this.repositoryInput().trim();
    if (query.length < 3) { this.error.set('Enter at least 3 search characters.'); return; }
    this.submittedRepositoryQuery.set(query);
    this.repositories.set([]); this.loadPage(1);
  }
  loadPage(page: number) {
    if (page < 1 || (this.mode() === 'user' && !this.selectedUsername())
        || (this.mode() === 'repository' && !this.submittedRepositoryQuery())) return;
    this.error.set(null); this.loading.set(true);
    this.loadRequests.next({ mode: this.mode(), username: this.selectedUsername(),
      query: this.submittedRepositoryQuery(), page });
  }
  selectRepo(repo: GithubRepo) {
    if (!this.storedIds().has(repo.id)) this.router.navigate(['/dashboard/repos', repo.owner.login, repo.name]);
  }
  private message(error: HttpErrorResponse): string {
    if (error.status === 401) return 'Your GitHub session needs to be refreshed. Sign in with GitHub again.';
    if (error.status === 429) return 'GitHub request limit reached. Please wait and try again.';
    if (error.status === 404) return 'GitHub user or repository not found or inaccessible.';
    return 'GitHub is unavailable right now. Please try again.';
  }
}
