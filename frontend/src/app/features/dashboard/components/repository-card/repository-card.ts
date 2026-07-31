import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GithubRepo } from '../../../../models/github.model';

@Component({
  selector: 'app-repository-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './repository-card.html'
})
export class RepositoryCard {
  @Input({ required: true }) repo!: GithubRepo;
}
