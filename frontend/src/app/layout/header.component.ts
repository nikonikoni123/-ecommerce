import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { CartService } from '../core/cart.service';

@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);
  protected readonly menuOpen = signal(false);

  /** El enlace de KPI aparece si el usuario puede ver algun indicador o gestionar metas. */
  protected canViewKpi(): boolean {
    return (
      this.auth.has('KPI_VIEW_ALL') ||
      this.auth.has('KPI_VIEW_TEAM') ||
      this.auth.has('KPI_VIEW_OWN') ||
      this.auth.has('KPI_GOAL_MANAGE')
    );
  }

  /** La administracion aparece si el usuario gestiona usuarios, cargos o departamentos. */
  protected canAdminister(): boolean {
    return (
      this.auth.has('USER_MANAGE') ||
      this.auth.has('ROLE_MANAGE') ||
      this.auth.has('DEPARTMENT_MANAGE')
    );
  }

  constructor() {
    // El contador se recarga al iniciar y cada vez que cambia la sesion.
    effect(() => {
      this.auth.user();
      this.cart.refreshCount();
    });
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected logout(): void {
    this.closeMenu();
    this.auth.logout();
  }
}
