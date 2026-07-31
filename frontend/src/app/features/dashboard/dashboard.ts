import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { StatsCard } from './components/stats-card/stats-card';
import { RepoSummary } from './components/repo-summary/repo-summary';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, StatsCard, RepoSummary],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit {
  private http = inject(HttpClient);
  
  dashboardData: any = null;
  loading = true;
  error = false;

  ngOnInit() {
    this.fetchDashboardData();
  }

  fetchDashboardData() {
    this.http.get('http://localhost:8080/api/dashboard/summary').subscribe({
      next: (data) => {
        this.dashboardData = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to fetch dashboard data', err);
        this.error = true;
        this.loading = false;
      }
    });
  }
}
