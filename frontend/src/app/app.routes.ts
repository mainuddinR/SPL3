import { Routes } from '@angular/router';
import { Login } from './features/login/login';
import { LoginSuccess } from './features/login-success/login-success';
import { authGuard } from './core/auth/auth-guard';
import { MainLayout } from './layout/main-layout/main-layout';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'login/success', component: LoginSuccess },
  { 
    path: '', 
    component: MainLayout,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard) },
      { path: 'dashboard/repos', loadComponent: () => import('./features/dashboard/components/repository-list/repository-list').then(m => m.RepositoryList) },
      { path: 'dashboard/repos/:owner/:repo', loadComponent: () => import('./features/dashboard/components/repository-details/repository-details').then(m => m.RepositoryDetails) },
      { path: 'dashboard/projects/:projectId/pulls', loadComponent: () => import('./features/pull-requests/pull-request-list/pull-request-list').then(m => m.PullRequestList) },
      { path: 'dashboard/pulls/:prId', loadComponent: () => import('./features/pull-requests/pull-request-details/pull-request-details').then(m => m.PullRequestDetails) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
