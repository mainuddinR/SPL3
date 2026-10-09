import { InfoPopover } from '../../components/info-popover/info-popover';
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { StatsCard } from './components/stats-card/stats-card';
import { RepoSummary } from './components/repo-summary/repo-summary';
import { DashboardResponse } from '../../models/dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, StatsCard, RepoSummary, InfoPopover],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {
  private http = inject(HttpClient);
  
  dashboardData: DashboardResponse | null = null;
  loading = true;
  error = false;
  notice = '';

  repositoryRemoved(id: number) {
    const repo = this.dashboardData?.repositories.find(item => item.id === id);
    this.notice = `${repo?.name || 'Repository'} removed.`;
    if (this.dashboardData) this.dashboardData.repositories = this.dashboardData.repositories.filter(item => item.id !== id);
    this.fetchDashboardData();
  }

  readonly categories = [
    { key: 'DESIGN', label: 'Design', tone: 'design' }, { key: 'DEFECT', label: 'Defect', tone: 'defect' },
    { key: 'TEST', label: 'Test', tone: 'test' }, { key: 'REQUIREMENT', label: 'Requirement', tone: 'requirement' },
    { key: 'DOCUMENTATION', label: 'Documentation', tone: 'documentation' },
    { key: 'UNCLASSIFIED', label: 'Unclassified', tone: 'unclassified' },
    { key: 'NOT_ASSESSED', label: 'Not Assessed', tone: 'not-assessed' }
  ];
  readonly severities = [
    { key: '1', label: 'Severity 1', tone: 'severity-1', description: 'Lowest' },
    { key: '2', label: 'Severity 2', tone: 'severity-2', description: 'Lower' },
    { key: '3', label: 'Severity 3', tone: 'severity-3', description: 'Moderate' },
    { key: '4', label: 'Severity 4', tone: 'severity-4', description: 'Higher' },
    { key: '5', label: 'Severity 5', tone: 'severity-5', description: 'Highest' },
    { key: 'NOT_ASSESSED', label: 'Not Assessed', tone: 'not-assessed', description: 'Unknown' }
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

  count(distribution: Record<string, number>, key: string): number {
    const value = distribution[key] ?? 0;
    return Number.isFinite(value) && value > 0 ? value : 0;
  }

  total(distribution: Record<string, number>, items: ReadonlyArray<{ key: string }>): number {
    return items.reduce((sum, item) => sum + this.count(distribution, item.key), 0);
  }

  percentage(count: number, total: number): number {
    return total > 0 ? Math.min(100, Math.max(0, (count / total) * 100)) : 0;
  }

  percentageLabel(count: number, total: number): string {
    return this.percentage(count, total).toFixed(1) + '%';
  }

  activeCategories(distribution: Record<string, number>): number {
    return this.categories.filter(item => item.key !== 'UNCLASSIFIED'
      && item.key !== 'NOT_ASSESSED' && this.count(distribution, item.key) > 0).length;
  }

  dominantLabel(distribution: Record<string, number>, items: ReadonlyArray<{ key: string; label: string }>): string {
    let dominant = '';
    let largest = 0;
    for (const item of items) {
      const value = this.count(distribution, item.key);
      if (value > largest) {
        dominant = item.label;
        largest = value;
      }
    }
    return dominant || 'None';
  }

  assessedSeverityCount(distribution: Record<string, number>): number {
    return this.severities.filter(item => item.key !== 'NOT_ASSESSED')
      .reduce((sum, item) => sum + this.count(distribution, item.key), 0);
  }

  highestSeverity(distribution: Record<string, number>): string {
    for (let score = 5; score >= 1; score--) {
      if (this.count(distribution, String(score)) > 0) return 'Level ' + score;
    }
    return 'None';
  }
}
