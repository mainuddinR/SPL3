import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DashboardApiService } from '../../services/dashboard-api.service';
import { DashboardFindingDetail } from '../../models/dashboard.model';
import { primarySuggestionSections, suggestionReviewNote } from '../pull-requests/pull-request-details/saved-suggestion';

@Component({
  selector: 'app-finding-detail', standalone: true,
  imports: [CommonModule, RouterModule], templateUrl: './finding-detail.html'
})
export class FindingDetail implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(DashboardApiService);
  detail = signal<DashboardFindingDetail | null>(null);
  loading = signal(true);
  error = signal(false);
  suggestionSections = primarySuggestionSections;
  suggestionReviewNote = suggestionReviewNote;

  ngOnInit() { this.load(); }
  load() {
    const id = Number(this.route.snapshot.paramMap.get('findingId'));
    if (!Number.isSafeInteger(id) || id < 1) { this.error.set(true); this.loading.set(false); return; }
    this.loading.set(true); this.error.set(false);
    this.api.findingDetail(id).subscribe({
      next: value => { this.detail.set(value); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }
}
