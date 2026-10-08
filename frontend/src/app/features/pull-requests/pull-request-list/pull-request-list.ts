import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { PullRequestService } from '../../../services/pull-request.service';
import { PullRequest } from '../../../models/pull-request.model';
import { NotificationService } from '../../../services/notification.service';

@Component({
  selector: 'app-pull-request-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './pull-request-list.html'
})
export class PullRequestList implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private prService = inject(PullRequestService);
  private notifications = inject(NotificationService);

  projectId = signal<number | null>(null);
  pullRequests = signal<PullRequest[]>([]);
  loading = signal<boolean>(true);
  syncing = signal<boolean>(false);
  error = signal<string | null>(null);

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const idStr = params.get('projectId');
      if (idStr) {
        const id = parseInt(idStr, 10);
        this.projectId.set(id);
        this.loadPullRequests(id);
      }
    });
  }

  loadPullRequests(projectId: number) {
    this.loading.set(true);
    this.prService.getPullRequests(projectId).subscribe({
      next: (data) => {
        this.pullRequests.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.error.set(err.status === 404 ? 'Repository not found or no longer available.' : 'Failed to load pull requests.');
        this.loading.set(false);
      }
    });
  }

  syncPullRequests() {
    const pId = this.projectId();
    if (!pId) return;

    this.syncing.set(true);
    this.prService.syncPullRequests(pId).subscribe({
      next: () => {
        this.syncing.set(false);
        this.notifications.refresh();
        this.loadPullRequests(pId);
      },
      error: (err) => {
        console.error(err);
        this.error.set('Failed to sync pull requests from GitHub.');
        this.syncing.set(false);
      }
    });
  }

  viewDetails(prId: number) {
    this.router.navigate(['/dashboard/pulls', prId]);
  }

  statusLabel(state: string): string {
    const normalized = state?.toLowerCase();
    if (normalized === 'open') return 'Open';
    if (normalized === 'closed') return 'Closed';
    if (normalized === 'merged') return 'Merged';
    return state || 'Unknown';
  }

  statusClass(state: string): string {
    const normalized = state?.toLowerCase();
    if (normalized === 'open' || normalized === 'closed' || normalized === 'merged') return `pr-state-${normalized}`;
    return '';
  }
}
