import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationApiService } from '../../core/account.service';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { AppNotification } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-notifications',
  imports: [RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="notifications">
      <div class="container">
        <p class="eyebrow">Actividad</p>
        <h1 class="notifications__title">Notificaciones</h1>

        <app-alert [message]="error()" variant="error" />

        @if (loading()) {
          <p class="notifications__state">Cargando...</p>
        } @else if (items().length === 0) {
          <p class="notifications__state">Todavia no tienes notificaciones.</p>
        } @else {
          <ul class="notifications__list">
            @for (item of items(); track item.id) {
              <li class="notification" [class.is-unread]="!item.read">
                <div class="notification__head">
                  <span class="eyebrow">{{ item.type }}</span>
                  <time class="notification__date">{{ date(item.createdAt) }}</time>
                </div>
                <h2 class="notification__title">{{ item.title }}</h2>
                <p class="notification__body">{{ item.body }}</p>
                <div class="notification__actions">
                  @if (item.link) {
                    <a class="btn btn--ghost btn--sm" [routerLink]="item.link">Ver</a>
                  }
                  @if (!item.read) {
                    <button class="btn btn--ghost btn--sm" type="button" (click)="markRead(item)">
                      Marcar como leida
                    </button>
                  }
                </div>
              </li>
            }
          </ul>
        }
      </div>
    </section>
  `,
  styles: `
    @use 'styles/tokens' as *;

    .notifications {
      @include section;
    }

    .notifications__title {
      margin-top: var(--space-4);
      margin-bottom: var(--space-8);
      font-size: var(--text-2xl);
    }

    .notifications__state {
      font-size: var(--text-sm);
      color: var(--ink-muted);
    }

    .notifications__list {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }

    .notification {
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      padding: var(--space-6);
    }

    /* La barra lateral marca lo no leido sin depender solo del color. */
    .notification.is-unread {
      border-left: 3px solid var(--accent);
    }

    .notification__head {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: var(--space-4);
      flex-wrap: wrap;
    }

    .notification__date {
      font-size: var(--text-xs);
      color: var(--ink-muted);
    }

    .notification__title {
      margin-top: var(--space-3);
      font-size: var(--text-base);
    }

    .notification__body {
      margin-top: var(--space-2);
      font-size: var(--text-sm);
      color: var(--ink-soft);
    }

    .notification__actions {
      display: flex;
      gap: var(--space-2);
      margin-top: var(--space-4);
    }
  `,
})
export class NotificationsComponent {
  private readonly api = inject(NotificationApiService);

  protected readonly items = signal<AppNotification[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly date = formatDate;

  constructor() {
    this.api.list(0, 50).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar tus notificaciones.'));
        this.loading.set(false);
      },
    });
  }

  protected markRead(item: AppNotification): void {
    this.api.markRead(item.id).subscribe({
      next: () =>
        this.items.update((list) =>
          list.map((entry) => (entry.id === item.id ? { ...entry, read: true } : entry)),
        ),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
