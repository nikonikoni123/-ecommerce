/** Etiquetas legibles de la clasificacion del BERT, compartidas entre las pantallas de casos. */

export function priorityLabel(priority: string): string {
  switch (priority) {
    case 'HIGH':
      return 'Alta';
    case 'LOW':
      return 'Baja';
    default:
      return 'Media';
  }
}

export function priorityClass(priority: string): string {
  return 'prio--' + (priority || 'medium').toLowerCase();
}

export function sentimentLabel(sentiment: string): string {
  switch (sentiment) {
    case 'NEGATIVE':
      return 'Negativo';
    case 'POSITIVE':
      return 'Positivo';
    default:
      return 'Neutral';
  }
}
