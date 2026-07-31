import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { GithubService } from '../../../../services/github.service';
import { GithubRepo, GithubBranch } from '../../../../models/github.model';

@Component({
  selector: 'app-repository-details',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './repository-details.html'
})
export class RepositoryDetails implements OnInit {
  private githubService = inject(GithubService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  repo = signal<GithubRepo | null>(null);
  branches = signal<GithubBranch[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  saving = signal<boolean>(false);

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const owner = params.get('owner');
      const repoName = params.get('repo');
      
      if (owner && repoName) {
        this.fetchRepositoryData(owner, repoName);
      } else {
        this.error.set('Invalid repository URL.');
        this.loading.set(false);
      }
    });
  }

  fetchRepositoryData(owner: string, repoName: string) {
    this.loading.set(true);
    this.error.set(null);

    this.githubService.getRepositoryDetails(owner, repoName).subscribe({
      next: (repoData) => {
        this.repo.set(repoData);
        this.fetchBranches(owner, repoName);
      },
      error: (err) => {
        console.error('Error fetching repo details:', err);
        this.error.set('Failed to fetch repository details.');
        this.loading.set(false);
      }
    });
  }

  fetchBranches(owner: string, repoName: string) {
    this.githubService.getBranches(owner, repoName).subscribe({
      next: (branchesData) => {
        this.branches.set(branchesData);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error fetching branches:', err);
        // We still show the repo details even if branches fail
        this.loading.set(false);
      }
    });
  }

  selectRepository() {
    const currentRepo = this.repo();
    if (!currentRepo) return;

    this.saving.set(true);
    this.githubService.selectRepository(currentRepo).subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        console.error('Error saving repository:', err);
        this.error.set(err.error?.message || 'Failed to save repository.');
        this.saving.set(false);
      }
    });
  }

  goBack() {
    this.router.navigate(['/dashboard/repos']);
  }
}
