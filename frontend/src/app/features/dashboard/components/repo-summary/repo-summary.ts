import { Component, Input, Output, EventEmitter } from '@angular/core';
import { RemoveRepository } from './remove-repository';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { RepositoryAnalysisSummary } from '../../../../models/dashboard.model';

@Component({
  selector: 'app-repo-summary',
  standalone: true,
  imports: [CommonModule, RouterModule, RemoveRepository],
  templateUrl: './repo-summary.html'
})
export class RepoSummary {
  @Input({ required: true }) repo!: RepositoryAnalysisSummary;
  @Output() removed = new EventEmitter<number>();
  removing = false;
  designStatus() {
    const status = this.repo.designProfileStatus;
    return ({ 'No supported patterns': 'No supported GoF patterns', 'Analysis failed': 'Failed' } as Record<string, string>)[status || ''] || status || 'Not analyzed';
  }
}
