import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-stats-card',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './stats-card.html'
})
export class StatsCard {
  @Input() title: string = '';
  @Input() value: number | string = '';
  @Input() icon: string = '';
  @Input() colorClass: string = 'text-blue-600 bg-blue-100';
  @Input({ required: true }) route: string = '/dashboard';
}
