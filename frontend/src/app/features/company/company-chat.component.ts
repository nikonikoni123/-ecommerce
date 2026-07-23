import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import { ChatMessageView, DepartmentChannel } from '../../core/models';
import { ChatService } from '../../core/support.service';
import { AlertComponent } from '../../shared/alert.component';

/** Un canal seleccionable: el general o un departamento. */
interface Channel {
  id: string; // 'general' o el id del departamento
  name: string;
  department: boolean;
}

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

  /** Canales disponibles: siempre el general, y los departamentos accesibles. */
  protected readonly channels = signal<Channel[]>([{ id: 'general', name: 'General', department: false }]);
  protected readonly active = signal<Channel>({ id: 'general', name: 'General', department: false });

  protected draft = '';
  /** El siguiente envio es un comunicado a toda la empresa, no un mensaje normal. */
  protected readonly broadcastMode = signal(false);

  protected readonly date = formatDate;
  protected readonly empty = computed(() => !this.loading() && this.messages().length === 0);
  protected readonly inDepartment = computed(() => this.active().department);

  constructor() {
    this.loadGeneral();
    this.api.departments().subscribe({
      next: (deps) => this.channels.update((list) => [...list, ...deps.map(toChannel)]),
      error: () => undefined,
    });
  }

  protected select(channel: Channel): void {
    if (channel.id === this.active().id) {
      return;
    }
    this.active.set(channel);
    this.broadcastMode.set(false);
    this.error.set(null);
    if (channel.department) {
      this.loadDepartment(channel.id);
    } else {
      this.loadGeneral();
    }
  }

  private loadGeneral(): void {
    this.loading.set(true);
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

  private loadDepartment(id: string): void {
    this.loading.set(true);
    // En un chat de equipo, quien tiene acceso puede escribir: no hace falta permiso especial.
    this.canBroadcast.set(false);
    this.canPost.set(true);
    this.api.departmentFeed(id, 0, 60).subscribe({
      next: (page) => {
        this.messages.set([...page.content].reverse());
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar el chat del equipo.'));
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
    const channel = this.active();
    const request = channel.department
      ? this.api.postToDepartment(channel.id, texto)
      : this.broadcastMode()
        ? this.api.broadcast(texto)
        : this.api.post(texto);

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

function toChannel(d: DepartmentChannel): Channel {
  return { id: d.id, name: d.name, department: true };
}
