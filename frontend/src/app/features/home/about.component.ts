import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-about',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="about">
      <div class="about__inner container">
        <p class="eyebrow">Nosotros</p>
        <h1 class="about__title">Vender y comprar, sin friccion</h1>
        <p class="about__text">
          Esta plataforma reune en un mismo lugar el catalogo, el inventario y los pedidos. Las
          empresas gestionan sus productos y su equipo con niveles de acceso a medida, y los
          compradores encuentran antes lo que buscan gracias a una priorizacion que aprende de su
          navegacion.
        </p>
        <p class="about__text">
          Cada cuenta protege su sesion con verificacion de correo y, si asi lo decide, con
          verificacion en dos pasos.
        </p>
        <div class="about__actions">
          @if (!auth.isAuthenticated()) {
            <a class="btn" routerLink="/auth/registro-usuario">Crear una cuenta</a>
            <a class="btn btn--outline" routerLink="/auth/registro-empresa">Registrar una empresa</a>
          }
        </div>
      </div>
    </section>
  `,
  styles: `
    @use 'styles/tokens' as *;

    .about {
      @include section;
    }

    .about__inner {
      @include container;
      max-width: 680px;
    }

    .about__title {
      margin-top: var(--space-6);
      font-size: var(--text-2xl);
    }

    .about__text {
      margin-top: var(--space-6);
      color: var(--ink-soft);
    }

    .about__actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-4);
      margin-top: var(--space-8);
    }
  `,
})
export class AboutComponent {
  public auth = inject(AuthService);
}