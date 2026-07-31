import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-repo-summary',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './repo-summary.html'
})
export class RepoSummary {
  @Input() repo: any;
}
