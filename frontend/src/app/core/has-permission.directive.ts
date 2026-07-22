import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Oculta un elemento cuando el usuario no tiene el permiso indicado.
 *
 * <p>Es solo una mejora de la interfaz: quien manda es el backend, que vuelve a comprobar el
 * permiso en cada peticion. Aqui solo se evita mostrar acciones que acabarian en un 403.
 *
 * <pre>&lt;button *hasPermission="'PRODUCT_DELETE'"&gt;Eliminar&lt;/button&gt;</pre>
 */
@Directive({
  selector: '[hasPermission]',
})
export class HasPermissionDirective {
  private readonly template = inject(TemplateRef<unknown>);
  private readonly container = inject(ViewContainerRef);
  private readonly auth = inject(AuthService);

  readonly hasPermission = input.required<string>();

  private rendered = false;

  constructor() {
    effect(() => {
      // Se lee la senal del usuario para reevaluar cuando cambie la sesion.
      this.auth.user();
      const allowed = this.auth.has(this.hasPermission());

      if (allowed && !this.rendered) {
        this.container.createEmbeddedView(this.template);
        this.rendered = true;
      } else if (!allowed && this.rendered) {
        this.container.clear();
        this.rendered = false;
      }
    });
  }
}
