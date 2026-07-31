import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { GithubService } from '../../../../services/github.service';
import { GithubRepo } from '../../../../models/github.model';
import { RepositoryCard } from '../repository-card/repository-card';

@Component({
  selector: 'app-repository-list',
  standalone: true,
  imports: [CommonModule, RepositoryCard],
  templateUrl: './repository-list.html'
})
export class RepositoryList implements OnInit, OnDestroy {
  private githubService = inject(GithubService);
  private router = inject(Router);

  repositories = signal<GithubRepo[]>([]);
  totalCount = signal<number>(0);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);
  searchQuery = signal<string>('angular');
  currentPage = signal<number>(1);

  mathMin = Math.min;

  private searchSubject = new Subject<string>();
  private searchSubscription?: Subscription;

  ngOnInit() {
    this.searchSubscription = this.searchSubject.pipe(
      debounceTime(500),
      distinctUntilChanged()
    ).subscribe(query => {
      this.searchQuery.set(query);
      this.currentPage.set(1);
      this.fetchRepositories();
    });

    this.fetchRepositories();
  }

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
  }

  fetchRepositories() {
    if (!this.searchQuery().trim()) {
      this.repositories.set([]);
      this.totalCount.set(0);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.githubService.searchRepositories(this.searchQuery(), this.currentPage()).subscribe({
      next: (response) => {
        this.repositories.set(response.items);
        this.totalCount.set(response.total_count);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error fetching repositories:', err);
        this.error.set('Failed to fetch repositories. Please try again.');
        this.loading.set(false);
      }
    });
  }

  onSearchInput(event: Event) {
    const query = (event.target as HTMLInputElement).value;
    this.searchSubject.next(query);
  }

  onPageChange(page: number) {
    this.currentPage.set(page);
    this.fetchRepositories();
  }

  selectRepo(repo: GithubRepo) {
    this.router.navigate(['/dashboard/repos', repo.owner.login, repo.name]);
  }
}
