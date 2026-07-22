import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Aviso en linea para errores de la API, confirmaciones y mensajes informativos. */
@Component({
  selector: 'app-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (message()) {
      <div class="alert" [class]="'alert--' + variant()" role="alert">
        <span>{{ message() }}</span>
      </div>
    }
  `,
})
export class AlertComponent {
  readonly message = input<string | null>(null);
  readonly variant = input<'error' | 'success' | 'info'>('error');
}
