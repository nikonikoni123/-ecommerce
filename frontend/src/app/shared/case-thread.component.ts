import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { formatDate } from '../core/format';
import { CaseMessageView } from '../core/models';

/** Hilo de mensajes de un caso, con burbujas alineadas segun quien escribe. */
@Component({
  selector: 'app-case-thread',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="thread">
      @for (m of messages(); track m.createdAt) {
        <li class="msg" [class.msg--mine]="isMine(m)">
          <div class="msg__bubble">
            <p class="msg__author">{{ m.authorName }}</p>
            <p class="msg__body">{{ m.body }}</p>
            <time class="msg__time">{{ date(m.createdAt) }}</time>
          </div>
        </li>
      }
    </ol>
  `,
  styles: `
    @use 'styles/tokens' as *;

    .thread {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }

    .msg {
      display: flex;
    }

    .msg--mine {
      justify-content: flex-end;
    }

    .msg__bubble {
      max-width: 78%;
      padding: var(--space-4);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: var(--surface);
    }

    .msg--mine .msg__bubble {
      background: var(--accent-soft);
      border-color: var(--accent);
    }

    .msg__author {
      @include eyebrow;
      margin-bottom: var(--space-2);
    }

    .msg__body {
      font-size: var(--text-sm);
      line-height: 1.7;
      color: var(--ink-soft);
      white-space: pre-wrap;
    }

    .msg__time {
      display: block;
      margin-top: var(--space-2);
      font-size: var(--text-xs);
      color: var(--ink-muted);
    }
  `,
})
export class CaseThreadComponent {
  readonly messages = input.required<CaseMessageView[]>();
  /** Autor cuyos mensajes se alinean a la derecha: CLIENTE o EMPRESA segun quien mira. */
  readonly mineAuthor = input.required<'CLIENTE' | 'EMPRESA'>();

  protected readonly date = formatDate;

  protected isMine(m: CaseMessageView): boolean {
    return m.author === this.mineAuthor();
  }
}
