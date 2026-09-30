import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { StatsCard } from './components/stats-card/stats-card';
import { RepoSummary } from './components/repo-summary/repo-summary';
import { DashboardResponse } from '../../models/dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, StatsCard, RepoSummary],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit {
  private http = inject(HttpClient);
  
  dashboardData: DashboardResponse | null = null;
  loading = true;
  error = false;

  readonly categories = [
    { key: 'DESIGN', label: 'Design' }, { key: 'DEFECT', label: 'Defect' },
    { key: 'TEST', label: 'Test' }, { key: 'REQUIREMENT', label: 'Requirement' },
    { key: 'DOCUMENTATION', label: 'Documentation' },
    { key: 'UNCLASSIFIED', label: 'Unclassified' },
    { key: 'NOT_ASSESSED', label: 'Not Assessed' }
  ];
  readonly severities = [
    { key: '1', label: 'Severity 1' }, { key: '2', label: 'Severity 2' },
    { key: '3', label: 'Severity 3' }, { key: '4', label: 'Severity 4' },
    { key: '5', label: 'Severity 5' }, { key: 'NOT_ASSESSED', label: 'Not Assessed' }
  ];

  ngOnInit() {
    this.fetchDashboardData();
  }

  fetchDashboardData() {
    this.loading = true;
    this.error = false;
    this.http.get<DashboardResponse>('http://localhost:8080/api/dashboard/summary').subscribe({
      next: (data) => {
        this.dashboardData = data;
        this.loading = false;
      },
      error: () => {
        this.error = true;
        this.loading = false;
      }
    });
  }

  barWidth(count: number, distribution: Record<string, number>): number {
    return count === 0 ? 0 : (count / Math.max(...Object.values(distribution), 1)) * 100;
  }
}
