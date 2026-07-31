import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-stats-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stats-card.html'
})
export class StatsCard {
  @Input() title: string = '';
  @Input() value: number | string = '';
  @Input() icon: string = '';
  @Input() colorClass: string = 'text-blue-600 bg-blue-100';
}
