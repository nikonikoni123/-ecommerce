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

// --------------------------------------------------------------------- carrito y pedidos

export interface CartLine {
  productId: string;
  name: string;
  slug: string | null;
  imageUrl: string | null;
  companyId: string | null;
  companyName: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  currency: string;
  /** El producto sigue publicado y con stock suficiente. */
  available: boolean;
  stockLeft: number;
}

export interface CompanyTotals {
  companyId: string;
  companyName: string;
  subtotal: number;
  discountAmount: number;
  taxableBase: number;
  taxAmount: number;
  shippingCost: number;
  total: number;
}

export interface DiscountLine {
  code: string;
  label: string;
  percent: number;
  amount: number;
}

export interface CartView {
  lines: CartLine[];
  byCompany: CompanyTotals[];
  discounts: DiscountLine[];
  subtotal: number;
  discountPercent: number;
  discountAmount: number;
  taxableBase: number;
  taxRate: number;
  taxAmount: number;
  shippingCost: number;
  total: number;
  currency: string;
  totalUnits: number;
  randomOrder: boolean;
  /** Hay lineas sin stock: no se puede pagar hasta resolverlas. */
  blocked: boolean;
  capped: boolean;
  promotionActive: boolean;
}

export interface GiftRequest {
  recipientName: string;
  address: string;
  postalCode: string;
  message?: string;
}

export interface CheckoutRequest {
  recipientName: string;
  address: string;
  postalCode: string;
  phone: string;
  paymentMethod?: string;
  asGift: boolean;
  gift?: GiftRequest | null;
}

export interface OrderSummary {
  id: string;
  number: string;
  companyId: string;
  companyName: string;
  status: string;
  statusLabel: string;
  terminal: boolean;
  itemCount: number;
  total: number;
  currency: string;
  randomOrder: boolean;
  gift: boolean;
  createdAt: string;
}

export interface OrderItemView {
  productId: string;
  name: string;
  slug: string | null;
  imageUrl: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface StatusChangeView {
  status: string;
  label: string;
  at: string;
  note: string | null;
}

export interface OrderDetail {
  id: string;
  number: string;
  companyId: string;
  companyName: string;
  customerName: string;
  customerEmail: string;
  items: OrderItemView[];
  subtotal: number;
  discounts: DiscountLine[];
  discountPercent: number;
  discountAmount: number;
  taxableBase: number;
  taxRate: number;
  taxAmount: number;
  shippingCost: number;
  total: number;
  currency: string;
  status: string;
  statusLabel: string;
  terminal: boolean;
  history: StatusChangeView[];
  randomOrder: boolean;
  shipping: { recipientName: string; address: string; postalCode: string; phone: string } | null;
  gift: {
    isGift: boolean;
    recipientName: string | null;
    address: string | null;
    postalCode: string | null;
    message: string | null;
  } | null;
  payment: { simulated: boolean; method: string; reference: string; paidAt: string } | null;
  createdAt: string;
  updatedAt: string;
}

export interface CheckoutResponse {
  orders: OrderSummary[];
  grandTotal: number;
  currency: string;
  paymentReference: string;
  message: string;
}

export interface SurpriseProposal {
  items: OrderItemView[];
  subtotal: number;
  estimatedDiscountPercent: number;
  estimatedTotal: number;
  currency: string;
  message: string;
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
