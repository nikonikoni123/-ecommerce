import { Routes } from '@angular/router';
import { authGuard, companyGuard, guestGuard, permissionGuard } from './core/guards';
import { Permission } from './core/models';

/**
 * Todas las pantallas se cargan de forma diferida: el visitante que solo mira el catalogo no
 * descarga el panel de gestion de la empresa.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
    title: 'E-Commerce',
  },
  {
    path: 'productos',
    loadComponent: () =>
      import('./features/catalog/catalog.component').then((m) => m.CatalogComponent),
    title: 'Tienda',
  },
  {
    path: 'productos/:slug',
    loadComponent: () =>
      import('./features/catalog/product-detail.component').then((m) => m.ProductDetailComponent),
  },
  {
    path: 'nosotros',
    loadComponent: () => import('./features/home/about.component').then((m) => m.AboutComponent),
    title: 'Nosotros',
  },

  // --- Autenticacion ---
  {
    path: 'auth/login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
    title: 'Ingresar',
  },
  {
    path: 'auth/registro-usuario',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register-customer.component').then((m) => m.RegisterCustomerComponent),
    title: 'Crear cuenta',
  },
  {
    path: 'auth/registro-empresa',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register-company.component').then((m) => m.RegisterCompanyComponent),
    title: 'Registrar empresa',
  },
  {
    path: 'auth/verificar',
    loadComponent: () => import('./features/auth/verify.component').then((m) => m.VerifyComponent),
    title: 'Verificar correo',
  },
  {
    path: 'auth/recuperar',
    loadComponent: () =>
      import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent),
    title: 'Recuperar contrasena',
  },
  {
    path: 'auth/restablecer',
    loadComponent: () =>
      import('./features/auth/reset-password.component').then((m) => m.ResetPasswordComponent),
    title: 'Nueva contrasena',
  },

  // --- Compra ---
  {
    path: 'carrito',
    canActivate: [authGuard],
    loadComponent: () => import('./features/cart/cart.component').then((m) => m.CartComponent),
    title: 'Carrito',
  },
  {
    path: 'checkout',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/cart/checkout.component').then((m) => m.CheckoutComponent),
    title: 'Finalizar compra',
  },
  {
    path: 'sorpresa',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/orders/surprise.component').then((m) => m.SurpriseComponent),
    title: 'Pedido sorpresa',
  },
  {
    path: 'pedidos',
    canActivate: [authGuard],
    loadComponent: () => import('./features/orders/orders.component').then((m) => m.OrdersComponent),
    title: 'Mis pedidos',
  },
  {
    path: 'pedidos/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/orders/order-detail.component').then((m) => m.OrderDetailComponent),
    title: 'Detalle del pedido',
  },

  // --- Zona autenticada ---
  {
    path: 'cuenta',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/account/account.component').then((m) => m.AccountComponent),
    title: 'Mi cuenta',
  },
  {
    path: 'notificaciones',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/account/notifications.component').then((m) => m.NotificationsComponent),
    title: 'Notificaciones',
  },

  // --- Panel de empresa ---
  {
    path: 'empresa/productos',
    canActivate: [companyGuard, permissionGuard],
    data: { permission: Permission.PRODUCT_VIEW },
    loadComponent: () =>
      import('./features/company/product-admin.component').then((m) => m.ProductAdminComponent),
    title: 'Gestion de productos',
  },

  { path: '**', redirectTo: '' },
];
