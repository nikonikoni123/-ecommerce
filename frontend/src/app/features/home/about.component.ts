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
            <a class="btn" routerLink="/auth/registro-usuario">Únete ahora</a>
            <a class="btn btn--outline" routerLink="/auth/registro-empresa">Registrar una empresa</a>
          }
        </div>
      </div>
    </section>
  `,
  styles: `
    @use 'styles/tokens' as *;

    /* El mismo lenguaje del hero de la home: degradado rojo a sangre y titular gigante en
       mayusculas. Los botones quedan con su estilo global, sin tocar. */
    .about {
      @include section;
      background: linear-gradient(150deg, var(--accent-dark), var(--accent) 45%, #f78c1f);
    }

    .about__inner {
      @include container;
      max-width: 680px;
      animation: rise 700ms cubic-bezier(0.22, 0.8, 0.3, 1) 150ms both;
    }

    @keyframes rise {
      from { opacity: 0; transform: translateY(28px); }
      to { opacity: 1; transform: none; }
    }

    .eyebrow {
      color: rgb(255 255 255 / 0.75);
    }

    .about__title {
      margin-top: var(--space-6);
      color: #fff;
      font-size: clamp(2.5rem, 6vw, 4.5rem);
      line-height: 0.98;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: -0.02em;
    }

    .about__text {
      margin-top: var(--space-6);
      color: #fff;
    }

    .about__actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-4);
      margin-top: var(--space-8);
    }

    @media (prefers-reduced-motion: reduce) {
      .about__inner { animation: none; }
    }
  `,
})
export class AboutComponent {
  public auth = inject(AuthService);
}