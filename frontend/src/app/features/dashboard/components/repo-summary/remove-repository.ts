import { API_ORIGIN } from '../../../../core/config/api-origin';
import { Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild, AfterViewInit, OnDestroy, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-remove-repository', standalone: true,
  template: `<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" (click)="cancel()">
    <section role="dialog" aria-modal="true" aria-labelledby="remove-repo-title" [attr.aria-busy]="busy" class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl border border-gray-200 bg-white p-5 shadow-xl sm:p-6" (click)="$event.stopPropagation()" (keydown.tab)="trap($event)">
      <h2 id="remove-repo-title" class="text-xl font-semibold">Remove repository?</h2>
      <p class="mt-3 break-all font-medium">{{ name }}</p>
      <p class="mt-3 text-sm text-gray-600">This removes the repository and its saved application data, including synchronized pull requests, analysis history, findings, notifications, and design profiles.</p>
      <p class="mt-3 text-sm font-medium">The GitHub repository itself will not be deleted.</p>
      @if (error) { <p role="alert" class="mt-3 text-sm text-red-700">{{ error }}</p> }
      <div class="mt-6 flex justify-end gap-3">
        <button #cancelButton type="button" class="rounded-lg border px-4 py-2 focus-visible:outline-2 focus-visible:outline-blue-600" [disabled]="busy" (click)="cancel()">Cancel</button>
        <button type="button" class="rounded-lg bg-red-600 px-4 py-2 text-white hover:bg-red-700 focus-visible:outline-2 focus-visible:outline-red-600 disabled:opacity-50" [disabled]="busy" (click)="remove()">{{ busy ? 'Deleting…' : 'Delete' }}</button>
      </div>
    </section>
  </div>`
})
export class RemoveRepository implements AfterViewInit, OnDestroy {
  @Input({required: true}) projectId!: number;
  @Input({required: true}) name!: string;
  @Output() closed = new EventEmitter<void>();
  @Output() removed = new EventEmitter<void>();
  @ViewChild('cancelButton') cancelButton!: ElementRef<HTMLButtonElement>;
  private http = inject(HttpClient);
  private host: ElementRef<HTMLElement> = inject(ElementRef);
  private previousFocus = document.activeElement as HTMLElement | null;
  busy = false;
  error = '';
  ngAfterViewInit() { this.cancelButton.nativeElement.focus(); }
  ngOnDestroy() { this.previousFocus?.focus(); }
  @HostListener('document:keydown.escape') cancel() { if (!this.busy) this.closed.emit(); }
  trap(event: Event) {
    const key = event as KeyboardEvent;
    const buttons = this.host.nativeElement.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
    if (!buttons.length) { key.preventDefault(); return; }
    const first = buttons[0], last = buttons[buttons.length - 1];
    if (key.shiftKey && document.activeElement === first) { key.preventDefault(); last.focus(); }
    else if (!key.shiftKey && document.activeElement === last) { key.preventDefault(); first.focus(); }
  }
  remove() {
    if (this.busy) return;
    this.busy = true; this.error = '';
    this.http.delete<void>(`${API_ORIGIN}/api/projects/${this.projectId}`).subscribe({
      next: () => this.removed.emit(),
      error: err => { this.busy = false; this.error = err.status === 409
        ? 'Wait for the running analysis to finish, then try again.'
        : 'Repository could not be removed. Please try again.'; }
    });
  }
}
