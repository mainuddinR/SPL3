import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Sidebar } from '../sidebar/sidebar';
import { Navbar } from '../navbar/navbar';
import { RouterModule } from '@angular/router';
import { SidebarService } from '../sidebar.service';
import { UiPreferencesService } from '../../services/ui-preferences.service';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, Sidebar, Navbar, RouterModule],
  templateUrl: './main-layout.html'
})
export class MainLayout {
  sidebarService = inject(SidebarService);
  preferences = inject(UiPreferencesService);
}
