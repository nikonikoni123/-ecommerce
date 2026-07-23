import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { ChatMessageView } from '../../core/models';
import { ChatService } from '../../core/support.service';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-company-chat',
  imports: [FormsModule, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-chat.component.html',
  styleUrl: './company-chat.component.scss',
})
export class CompanyChatComponent {
  private readonly api = inject(ChatService);

  /** Mensajes en orden cronologico (el backend los devuelve del mas nuevo al mas viejo). */
  protected readonly messages = signal<ChatMessageView[]>([]);
  protected readonly canPost = signal(false);
  protected readonly canBroadcast = signal(false);
  protected readonly loading = signal(true);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected draft = '';
  /** El siguiente envio es un comunicado a toda la empresa, no un mensaje normal. */
  protected readonly broadcastMode = signal(false);

  protected readonly date = formatDate;
  protected readonly empty = computed(() => !this.loading() && this.messages().length === 0);

  constructor() {
    this.load();
  }

  private load(): void {
    this.api.feed(0, 60).subscribe({
      next: (feed) => {
        this.messages.set([...feed.messages.content].reverse());
        this.canPost.set(feed.canPost);
        this.canBroadcast.set(feed.canBroadcast);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el chat.'));
        this.loading.set(false);
      },
    });
  }

  protected send(): void {
    if (!this.draft.trim() || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);

    const texto = this.draft.trim();
    const request = this.broadcastMode() ? this.api.broadcast(texto) : this.api.post(texto);

    request.subscribe({
      next: (msg) => {
        this.messages.update((list) => [...list, msg]);
        this.draft = '';
        this.sending.set(false);
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(errorMessage(err, 'No pudimos enviar el mensaje.'));
      },
    });
  }
}
