import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      // Al navegar se vuelve arriba, salvo cuando el usuario usa atras o adelante.
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor])),
    /**
     * La sesion ya no se guarda en el navegador: vive en cookies httpOnly que este no puede leer.
     * Por eso al arrancar hay que preguntarle al servidor quien es el usuario, y hacerlo *antes* de
     * que se evaluen las guardas de ruta; si no, recargar en una pagina protegida rebotaria al
     * inicio aunque la sesion siguiera siendo valida.
     */
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restoreSession())),
  ],
};
