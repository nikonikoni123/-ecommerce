import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AccountService } from '../../core/account.service';
import { errorMessage } from '../../core/api-error';
import { CartService, OrderService } from '../../core/cart.service';
import { formatPrice } from '../../core/format';
import { CartView, CheckoutResponse } from '../../core/models';
import { AlertComponent } from '../../shared/alert.component';

@Component({
  selector: 'app-checkout',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
})
export class CheckoutComponent {
  private readonly fb = inject(FormBuilder);
  private readonly cart = inject(CartService);
  private readonly ordersApi = inject(OrderService);
  private readonly account = inject(AccountService);
  private readonly router = inject(Router);

  protected readonly view = signal<CartView | null>(null);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal<CheckoutResponse | null>(null);

  protected readonly price = formatPrice;

  protected readonly form = this.fb.nonNullable.group({
    recipientName: ['', [Validators.required]],
    address: ['', [Validators.required]],
    postalCode: ['', [Validators.required]],
    phone: ['', [Validators.required]],
    paymentMethod: ['Tarjeta simulada'],
    asGift: [false],
    giftRecipientName: [''],
    giftAddress: [''],
    giftPostalCode: [''],
    giftMessage: [''],
  });

  constructor() {
    this.cart.view().subscribe({
      next: (v) => {
        this.view.set(v);
        this.loading.set(false);
        if (!v.lines.length) {
          this.router.navigate(['/carrito']);
        }
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'No pudimos cargar tu carrito.'));
        this.loading.set(false);
      },
    });

    // Se rellena la direccion con la del perfil, que es lo habitual, y el cliente la ajusta.
    this.account.profile().subscribe({
      next: (p) =>
        this.form.patchValue({
          recipientName: [p.firstName, p.lastName].filter(Boolean).join(' '),
          address: p.address ?? '',
          postalCode: p.postalCode ?? '',
          phone: p.phone ?? '',
        }),
      error: () => undefined,
    });

    // Los datos del regalo solo son obligatorios cuando la casilla esta marcada.
    this.form.controls.asGift.valueChanges.subscribe((asGift) => {
      const campos = [
        this.form.controls.giftRecipientName,
        this.form.controls.giftAddress,
        this.form.controls.giftPostalCode,
      ];
      for (const campo of campos) {
        campo.setValidators(asGift ? [Validators.required] : []);
        campo.updateValueAndValidity();
      }
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const v = this.form.getRawValue();

    this.ordersApi
      .checkout({
        recipientName: v.recipientName,
        address: v.address,
        postalCode: v.postalCode,
        phone: v.phone,
        paymentMethod: v.paymentMethod,
        // La condicion de pedido sorpresa la aporta el carrito en el servidor, no esta pantalla.
        asGift: v.asGift,
        gift: v.asGift
          ? {
              recipientName: v.giftRecipientName,
              address: v.giftAddress,
              postalCode: v.giftPostalCode,
              message: v.giftMessage,
            }
          : null,
      })
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.done.set(response);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(errorMessage(err, 'No pudimos completar el pago.'));
        },
      });
  }
}
