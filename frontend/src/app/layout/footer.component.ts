import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer class="footer">
      <div class="footer__inner container">
        <div>
          <p class="eyebrow">E&#8209;Commerce</p>
          <p class="footer__tagline">
            Catalogo, inventario y pedidos en una sola plataforma.
          </p>
        </div>
        <nav class="footer__nav">
          <a routerLink="/productos">Tienda</a>
          <a routerLink="/nosotros">Nosotros</a>
          <a routerLink="/auth/registro-empresa">Vender aqui</a>
        </nav>
      </div>
    </footer>
  `,
  styles: `
    @use '../../styles/tokens' as *;

    .footer {
      border-top: 1px solid var(--line);
      margin-top: var(--space-24);
      padding-block: var(--space-12);
    }

    .footer__inner {
      @include container;
      display: flex;
      flex-direction: column;
      gap: var(--space-8);
      justify-content: space-between;

      @media (min-width: 768px) {
        flex-direction: row;
        align-items: flex-start;
      }
    }

    .footer__tagline {
      margin-top: var(--space-3);
      font-size: var(--text-sm);
      color: var(--ink-muted);
      max-width: 32ch;
    }

    .footer__nav {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);

      a {
        font-size: var(--text-sm);
        color: var(--ink-muted);

        &:hover {
          color: var(--ink);
        }
      }
    }
  `,
})
export class FooterComponent {}
