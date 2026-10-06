import { Component, computed, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Subject, Subscription, debounceTime, distinctUntilChanged, switchMap, catchError, of, map } from 'rxjs';
import { GithubService } from '../../../../services/github.service';
import { GithubRepo, RepositoryPage } from '../../../../models/github.model';
import { RepositoryCard } from '../repository-card/repository-card';

type Mode = 'mine' | 'user';
type LoadRequest = { mode: Mode; username: string; page: number };

@Component({
  selector: 'app-repository-list',
  standalone: true,
  imports: [CommonModule, RepositoryCard],
  templateUrl: './repository-list.html'
})
export class RepositoryList implements OnInit, OnDestroy {
  private githubService = inject(GithubService);
  private router = inject(Router);
  private filterInput = new Subject<string>();
  private loadRequests = new Subject<LoadRequest>();
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
  currentPage = signal(1);
  hasNext = signal(false);
  loading = signal(false);
  lookupLoading = signal(false);
  error = signal<string | null>(null);

  ngOnInit() {
    this.subscriptions.add(this.filterInput.pipe(debounceTime(400), distinctUntilChanged())
      .subscribe(value => { this.filter.set(value); this.filterPending.set(false); }));
    this.subscriptions.add(this.loadRequests.pipe(
      switchMap(request => {
        if (request.mode === 'user' && !request.username) return EMPTY;
        const response = request.mode === 'mine'
          ? this.githubService.myRepositories(request.page)
          : this.githubService.userRepositories(request.username, request.page);
        return response.pipe(
          map(page => ({ page, error: null as HttpErrorResponse | null })),
          catchError(error => of({ page: null as RepositoryPage | null, error: error as HttpErrorResponse }))
        );
      })
    ).subscribe(result => {
      this.loading.set(false);
      this.lookupLoading.set(false);
      if (result.error) {
        this.repositories.set([]);
        this.hasNext.set(false);
        this.error.set(this.message(result.error));
        return;
      }
      this.repositories.set(result.page!.repositories);
      this.storedIds.set(new Set(result.page!.storedRepositoryIds));
      this.hasNext.set(result.page!.hasNext);
      this.currentPage.set(result.page!.page);
      this.error.set(null);
    }));
    this.loadPage(1);
  }

  ngOnDestroy() { this.subscriptions.unsubscribe(); }

  setMode(mode: Mode) {
    if (this.mode() === mode) return;
    this.mode.set(mode);
    this.repositories.set([]);
    this.filter.set('');
    this.filterInput.next('');
    this.filterPending.set(false);
    this.error.set(null);
    this.currentPage.set(1);
    this.hasNext.set(false);
    if (mode === 'mine') this.loadPage(1);
    else {
      this.selectedUsername.set('');
      this.loadRequests.next({ mode: 'user', username: '', page: 1 });
      this.loading.set(false);
      this.lookupLoading.set(false);
    }
  }

  onFilterInput(event: Event) {
    const value = (event.target as HTMLInputElement).value.trim();
    this.filterPending.set(true);
    if (!value) { this.filter.set(''); this.filterPending.set(false); }
    this.filterInput.next(value);
  }

  onUsernameInput(event: Event) { this.usernameInput.set((event.target as HTMLInputElement).value.trim()); }

  findUser() {
    const username = this.usernameInput();
    if (!/^[A-Za-z0-9-]{1,39}$/.test(username)) {
      this.error.set('Enter an exact GitHub username.'); return;
    }
    this.selectedUsername.set(username);
    this.repositories.set([]);
    this.filter.set('');
    this.filterInput.next('');
    this.loadPage(1);
  }

  loadPage(page: number) {
    if (page < 1 || (this.mode() === 'user' && !this.selectedUsername())) return;
    this.error.set(null);
    this.loading.set(true);
    this.lookupLoading.set(this.mode() === 'user');
    this.loadRequests.next({ mode: this.mode(), username: this.selectedUsername(), page });
  }

  selectRepo(repo: GithubRepo) {
    if (!this.storedIds().has(repo.id)) this.router.navigate(['/dashboard/repos', repo.owner.login, repo.name]);
  }

  private message(error: HttpErrorResponse): string {
    if (error.status === 401) return 'Your GitHub session needs to be refreshed. Sign in with GitHub again.';
    if (error.status === 429) return 'GitHub request limit reached. Please wait a moment and try again.';
    if (error.status === 404) return 'GitHub user not found or inaccessible.';
    return 'GitHub repositories are unavailable right now. Please try again.';
  }
}
