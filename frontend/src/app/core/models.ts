/** Tipos compartidos con la API. Reflejan los DTO del backend. */

export type UserType = 'CUSTOMER' | 'COMPANY_MEMBER';

export interface UserSummary {
  id: string;
  email: string;
  type: UserType;
  displayName: string;
  companyId: string | null;
  companyName: string | null;
  root: boolean;
  twoFactorEnabled: boolean;
  permissions: string[];
}

export interface AuthResponse {
  accessToken: string | null;
  refreshToken: string | null;
  expiresInSeconds: number;
  /** Las credenciales eran correctas pero falta el codigo del segundo factor. */
  twoFactorRequired: boolean;
  challengeToken: string | null;
  /** La 2FA esta desactivada: hay que mostrar el recordatorio. */
  twoFactorReminder: boolean;
  user: UserSummary | null;
}

export interface Profile {
  id: string;
  type: UserType;
  email: string;
  firstName: string | null;
  lastName: string | null;
  username: string | null;
  address: string | null;
  postalCode: string | null;
  phone: string | null;
  gender: string | null;
  emailVerified: boolean;
  twoFactorEnabled: boolean;
  twoFactorReminder: boolean;
  companyId: string | null;
  companyName: string | null;
  root: boolean;
  position: string | null;
  permissions: string[];
}

export interface ProductSummary {
  id: string;
  slug: string;
  name: string;
  companyId: string;
  companyName: string;
  price: number;
  currency: string;
  inStock: boolean;
  images: string[];
  categories: string[];
  tags: string[];
}

export interface ProductDetail extends Omit<ProductSummary, 'inStock'> {
  description: string;
  stock: number;
  inStock: boolean;
}

export interface CompanyProduct {
  id: string;
  slug: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  stock: number;
  active: boolean;
  images: string[];
  categories: string[];
  visibleTags: string[];
  hiddenTags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  timestamp: string;
}

export interface MessageResponse {
  message: string;
}

export interface TwoFactorSetup {
  secret: string;
  otpAuthUri: string;
}

/** Permisos del backend usados por la interfaz. Debe coincidir con el enum Permission de Java. */
export const Permission = {
  PRODUCT_VIEW: 'PRODUCT_VIEW',
  PRODUCT_CREATE: 'PRODUCT_CREATE',
  PRODUCT_UPDATE: 'PRODUCT_UPDATE',
  PRODUCT_DELETE: 'PRODUCT_DELETE',
  STOCK_UPDATE: 'STOCK_UPDATE',
  USER_MANAGE: 'USER_MANAGE',
  ROLE_MANAGE: 'ROLE_MANAGE',
  ACTIVITY_VIEW: 'ACTIVITY_VIEW',
} as const;
