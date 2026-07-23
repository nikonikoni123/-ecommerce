import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { KpiSlice } from '../core/models';
import { formatPrice } from '../core/format';

/**
 * Graficas SVG propias, sin ninguna libreria externa.
 *
 * <p>Se dibujan a mano con SVG y los tokens del sistema de diseno, coherentes con el resto de la
 * interfaz: paleta salvia/arena, trazos finos, mucho aire. Cada grafica recibe una lista de
 * {@link KpiSlice} (etiqueta + valor) y se adapta a su contenedor con un viewBox.
 */

/** Paleta de la marca para las series categoricas (dona, barras multicolor). */
const PALETTE = ['#7d8c74', '#a8b5a0', '#c7b299', '#8c9db8', '#b08a8a', '#9a9a7d', '#6b8c8c', '#c9a05a'];

function fmt(value: number, unit: string): string {
  return unit === 'CURRENCY' ? formatPrice(value) : new Intl.NumberFormat('es-CO').format(value);
}

// ===================================================================== barras horizontales

@Component({
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (slices().length === 0) {
      <p class="chart-empty">Sin datos en el periodo.</p>
    } @else {
      <div class="bars">
        @for (bar of bars(); track bar.label) {
          <div class="bars__row">
            <span class="bars__label" [title]="bar.label">{{ bar.label }}</span>
            <div class="bars__track">
              <div class="bars__fill" [style.width.%]="bar.pct"></div>
            </div>
            <span class="bars__value">{{ bar.text }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .chart-empty {
        font-size: var(--text-sm);
        color: var(--ink-muted);
        padding: var(--space-4) 0;
      }
      .bars {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .bars__row {
        display: grid;
        grid-template-columns: minmax(90px, 30%) 1fr auto;
        align-items: center;
        gap: var(--space-3);
      }
      .bars__label {
        font-size: var(--text-sm);
        color: var(--ink-soft);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .bars__track {
        background: var(--surface-alt);
        border-radius: 999px;
        height: 10px;
        overflow: hidden;
      }
      .bars__fill {
        height: 100%;
        background: var(--accent-dark);
        border-radius: 999px;
        transition: width 400ms ease;
        min-width: 2px;
      }
      .bars__value {
        font-size: var(--text-sm);
        color: var(--ink);
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class BarChartComponent {
  readonly slices = input<KpiSlice[]>([]);
  readonly unit = input<string>('COUNT');

  protected readonly bars = computed(() => {
    const data = this.slices();
    const max = Math.max(1, ...data.map((s) => s.value));
    return data.map((s) => ({
      label: s.label,
      pct: (s.value / max) * 100,
      text: fmt(s.value, this.unit()),
    }));
  });
}

// ===================================================================== dona

@Component({
  selector: 'app-donut-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() === 0) {
      <p class="chart-empty">Sin datos en el periodo.</p>
    } @else {
      <div class="donut">
        <svg viewBox="0 0 42 42" class="donut__svg" role="img" [attr.aria-label]="label()">
          <circle class="donut__hole" cx="21" cy="21" r="15.915" />
          @for (arc of arcs(); track arc.label) {
            <circle
              class="donut__seg"
              cx="21"
              cy="21"
              r="15.915"
              [attr.stroke]="arc.color"
              [attr.stroke-dasharray]="arc.dash"
              [attr.stroke-dashoffset]="arc.offset"
            />
          }
          <text x="21" y="20.5" class="donut__total">{{ total() }}</text>
          <text x="21" y="25.5" class="donut__caption">total</text>
        </svg>
        <ul class="donut__legend">
          @for (arc of arcs(); track arc.label) {
            <li>
              <span class="donut__dot" [style.background]="arc.color"></span>
              <span class="donut__name">{{ arc.label }}</span>
              <span class="donut__count">{{ arc.value }}</span>
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .chart-empty {
        font-size: var(--text-sm);
        color: var(--ink-muted);
        padding: var(--space-4) 0;
      }
      .donut {
        display: flex;
        align-items: center;
        gap: var(--space-8);
        flex-wrap: wrap;
      }
      .donut__svg {
        width: 160px;
        height: 160px;
        flex-shrink: 0;
        transform: rotate(-90deg);
      }
      .donut__hole {
        fill: none;
        stroke: var(--surface-alt);
        stroke-width: 3;
      }
      .donut__seg {
        fill: none;
        stroke-width: 3;
        transition: stroke-dasharray 400ms ease;
      }
      .donut__total {
        font-size: 8px;
        font-weight: 600;
        fill: var(--ink);
        text-anchor: middle;
        transform: rotate(90deg);
        transform-origin: 21px 21px;
      }
      .donut__caption {
        font-size: 2.6px;
        letter-spacing: 0.18em;
        text-transform: uppercase;
        fill: var(--ink-muted);
        text-anchor: middle;
        transform: rotate(90deg);
        transform-origin: 21px 21px;
      }
      .donut__legend {
        list-style: none;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        min-width: 140px;
      }
      .donut__legend li {
        display: grid;
        grid-template-columns: auto 1fr auto;
        align-items: center;
        gap: var(--space-3);
        font-size: var(--text-sm);
      }
      .donut__dot {
        width: 10px;
        height: 10px;
        border-radius: 999px;
      }
      .donut__name {
        color: var(--ink-soft);
      }
      .donut__count {
        color: var(--ink);
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class DonutChartComponent {
  readonly slices = input<KpiSlice[]>([]);
  readonly label = input<string>('Reparto');

  protected readonly total = computed(() =>
    this.slices().reduce((sum, s) => sum + s.value, 0),
  );

  protected readonly arcs = computed(() => {
    const total = this.total();
    if (total === 0) return [];
    let acc = 0;
    return this.slices()
      .filter((s) => s.value > 0)
      .map((s, i) => {
        const pct = (s.value / total) * 100;
        const arc = {
          label: s.label,
          value: s.value,
          color: PALETTE[i % PALETTE.length],
          dash: `${pct} ${100 - pct}`,
          // El desfase acumulado coloca cada segmento a continuacion del anterior.
          offset: 100 - acc + 25,
        };
        acc += pct;
        return arc;
      });
  });
}

// ===================================================================== linea

@Component({
  selector: 'app-line-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (slices().length === 0) {
      <p class="chart-empty">Sin datos en el periodo.</p>
    } @else {
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" class="line" role="img" [attr.aria-label]="label()">
        <!-- rejilla horizontal -->
        @for (g of gridLines(); track g.y) {
          <line class="line__grid" [attr.x1]="pad" [attr.x2]="W - pad" [attr.y1]="g.y" [attr.y2]="g.y" />
          <text class="line__gridlabel" [attr.x]="pad - 6" [attr.y]="g.y + 3">{{ g.text }}</text>
        }
        <!-- area y trazo -->
        <path class="line__area" [attr.d]="areaPath()" />
        <path class="line__stroke" [attr.d]="linePath()" />
        @for (p of points(); track p.label) {
          <circle class="line__dot" [attr.cx]="p.x" [attr.cy]="p.y" r="3" />
          <text class="line__xlabel" [attr.x]="p.x" [attr.y]="H - 6">{{ p.label }}</text>
        }
      </svg>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .chart-empty {
        font-size: var(--text-sm);
        color: var(--ink-muted);
        padding: var(--space-4) 0;
      }
      .line {
        width: 100%;
        height: auto;
      }
      .line__grid {
        stroke: var(--line);
        stroke-width: 0.5;
      }
      .line__gridlabel {
        font-size: 7px;
        fill: var(--ink-muted);
        text-anchor: end;
      }
      .line__area {
        fill: var(--accent);
        opacity: 0.18;
      }
      .line__stroke {
        fill: none;
        stroke: var(--accent-dark);
        stroke-width: 1.5;
        stroke-linejoin: round;
        stroke-linecap: round;
      }
      .line__dot {
        fill: var(--surface);
        stroke: var(--accent-dark);
        stroke-width: 1.5;
      }
      .line__xlabel {
        font-size: 7px;
        fill: var(--ink-muted);
        text-anchor: middle;
      }
    `,
  ],
})
export class LineChartComponent {
  readonly slices = input<KpiSlice[]>([]);
  readonly unit = input<string>('CURRENCY');
  readonly label = input<string>('Serie');

  protected readonly W = 320;
  protected readonly H = 160;
  protected readonly pad = 34;

  private readonly max = computed(() => Math.max(1, ...this.slices().map((s) => s.value)));

  protected readonly points = computed(() => {
    const data = this.slices();
    const n = data.length;
    const innerW = this.W - this.pad * 2;
    const innerH = this.H - this.pad - 20;
    const max = this.max();
    return data.map((s, i) => ({
      label: s.label,
      x: this.pad + (n <= 1 ? innerW / 2 : (innerW * i) / (n - 1)),
      y: this.pad + innerH - (s.value / max) * innerH,
      value: s.value,
    }));
  });

  protected readonly linePath = computed(() =>
    this.points().map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x} ${p.y}`).join(' '),
  );

  protected readonly areaPath = computed(() => {
    const pts = this.points();
    if (pts.length === 0) return '';
    const base = this.H - 20;
    const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x} ${p.y}`).join(' ');
    return `${line} L${pts[pts.length - 1].x} ${base} L${pts[0].x} ${base} Z`;
  });

  protected readonly gridLines = computed(() => {
    const max = this.max();
    const innerH = this.H - this.pad - 20;
    const steps = 4;
    return Array.from({ length: steps + 1 }, (_, i) => {
      const value = (max / steps) * (steps - i);
      return {
        y: this.pad + (innerH * i) / steps,
        text: fmt(value, this.unit()).replace(/\s/g, ''),
      };
    });
  });
}

// ===================================================================== medidor de progreso

@Component({
  selector: 'app-gauge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 42 24" class="gauge" role="img" [attr.aria-label]="'Progreso ' + display()">
      <path class="gauge__track" d="M4 21 A17 17 0 0 1 38 21" />
      <path
        class="gauge__value"
        d="M4 21 A17 17 0 0 1 38 21"
        [attr.stroke]="color()"
        [attr.stroke-dasharray]="dash()"
      />
      <text x="21" y="19" class="gauge__pct">{{ display() }}</text>
    </svg>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .gauge {
        width: 100%;
        max-width: 160px;
      }
      .gauge__track {
        fill: none;
        stroke: var(--surface-alt);
        stroke-width: 3.4;
        stroke-linecap: round;
      }
      .gauge__value {
        fill: none;
        stroke-width: 3.4;
        stroke-linecap: round;
        transition: stroke-dasharray 500ms ease;
      }
      .gauge__pct {
        font-size: 7px;
        font-weight: 600;
        fill: var(--ink);
        text-anchor: middle;
      }
    `,
  ],
})
export class GaugeComponent {
  /** Porcentaje 0-100+ de avance. */
  readonly percent = input<number>(0);

  // La longitud del semicirculo de radio 17 es PI * 17 ~= 53.4.
  private readonly ARC = 53.4;

  protected readonly display = computed(() => `${Math.round(this.percent())}%`);

  protected readonly color = computed(() => {
    const p = this.percent();
    if (p >= 100) return 'var(--success)';
    if (p >= 60) return 'var(--accent-dark)';
    return 'var(--danger)';
  });

  protected readonly dash = computed(() => {
    const filled = (Math.min(this.percent(), 100) / 100) * this.ARC;
    return `${filled} ${this.ARC - filled}`;
  });
}
