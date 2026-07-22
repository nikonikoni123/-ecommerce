package com.ecommerce.order;

import com.ecommerce.company.CompanyRepository;
import com.ecommerce.config.AppProperties;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
// OpenPDF 3 renombro su paquete de com.lowagie.text a org.openpdf.text.
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

/**
 * Factura del pedido en PDF.
 *
 * <p>Hereda el lenguaje visual del resto del proyecto: fondo claro, microetiquetas en mayusculas con
 * mucho espaciado y practicamente nada de color. Incluye el <b>desglose de cada descuento</b>, que
 * es justo lo que el cliente necesita para entender por que el total no coincide con la suma de los
 * precios de catalogo.
 *
 * <p>Se genera al vuelo desde el pedido en lugar de guardarse: el documento ya lleva los importes
 * congelados, asi que la factura siempre sale igual aunque cambien precios o promociones.
 */
@Service
public class InvoiceService {

    private static final Color TINTA = new Color(0x1A, 0x1A, 0x1A);
    private static final Color SUAVE = new Color(0x6B, 0x68, 0x62);
    private static final Color LINEA = new Color(0xE3, 0xDD, 0xD4);
    private static final Color CREMA = new Color(0xF7, 0xF4, 0xEF);

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final CompanyRepository companies;
    private final AppProperties properties;

    public InvoiceService(CompanyRepository companies, AppProperties properties) {
        this.companies = companies;
        this.properties = properties;
    }

    public byte[] render(Order order) {
        var salida = new ByteArrayOutputStream();
        var documento = new Document(PageSize.A4, 48, 48, 48, 48);

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            cabecera(documento, order);
            partes(documento, order);
            lineas(documento, order);
            totales(documento, order);
            pie(documento, order);

            documento.close();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar la factura del pedido "
                    + order.getNumber(), e);
        }

        return salida.toByteArray();
    }

    // ------------------------------------------------------------------ bloques

    private void cabecera(Document doc, Order order) {
        doc.add(etiqueta("Factura"));

        var titulo = new Paragraph(order.getNumber(), fuente(22, Font.NORMAL, TINTA));
        titulo.setSpacingBefore(6);
        titulo.setSpacingAfter(4);
        doc.add(titulo);

        var fecha = new Paragraph("Emitida el " + FECHA.format(order.getCreatedAt()),
                fuente(9, Font.NORMAL, SUAVE));
        fecha.setSpacingAfter(24);
        doc.add(fecha);
    }

    /** Vendedor y comprador, uno al lado del otro. */
    private void partes(Document doc, Order order) throws Exception {
        var tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(24);

        var empresa = companies.findById(order.getCompanyId());
        var vendedor = new StringBuilder(order.getCompanyName());
        empresa.ifPresent(c -> {
            if (c.getNit() != null) {
                vendedor.append("\nNIT ").append(c.getNit());
            }
            if (c.getAddress() != null) {
                vendedor.append('\n').append(c.getAddress());
            }
            if (c.getEmail() != null) {
                vendedor.append('\n').append(c.getEmail());
            }
        });

        // La entrega puede ir a un tercero cuando el pedido es un regalo.
        var entrega = order.deliveryAddress();
        var comprador = new StringBuilder(order.getCustomerName())
                .append('\n').append(order.getCustomerEmail());
        if (entrega != null) {
            comprador.append("\n\nEntregar a:\n").append(entrega.getRecipientName())
                    .append('\n').append(entrega.getAddress())
                    .append("\nCP ").append(entrega.getPostalCode());
        }

        tabla.addCell(bloque("Vendedor", vendedor.toString()));
        tabla.addCell(bloque("Cliente", comprador.toString()));
        doc.add(tabla);
    }

    private void lineas(Document doc, Order order) throws Exception {
        var tabla = new PdfPTable(new float[]{46, 14, 20, 20});
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(16);

        tabla.addCell(th("Producto", Element.ALIGN_LEFT));
        tabla.addCell(th("Cantidad", Element.ALIGN_CENTER));
        tabla.addCell(th("Precio unitario", Element.ALIGN_RIGHT));
        tabla.addCell(th("Importe", Element.ALIGN_RIGHT));

        for (var item : order.getItems()) {
            tabla.addCell(td(item.getName(), Element.ALIGN_LEFT));
            tabla.addCell(td(String.valueOf(item.getQuantity()), Element.ALIGN_CENTER));
            tabla.addCell(td(dinero(item.getUnitPrice(), order.getCurrency()), Element.ALIGN_RIGHT));
            tabla.addCell(td(dinero(item.getLineTotal(), order.getCurrency()), Element.ALIGN_RIGHT));
        }

        doc.add(tabla);
    }

    private void totales(Document doc, Order order) throws Exception {
        var tabla = new PdfPTable(new float[]{60, 40});
        tabla.setWidthPercentage(60);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setSpacingAfter(24);

        String moneda = order.getCurrency();

        fila(tabla, "Subtotal", dinero(order.getSubtotal(), moneda), false);

        // El desglose linea a linea es lo que justifica la rebaja frente al cliente.
        for (var d : order.getDiscounts()) {
            String etiqueta = "%s (%s%%)".formatted(d.getLabel(),
                    d.getPercent().stripTrailingZeros().toPlainString());
            fila(tabla, etiqueta, "-" + dinero(d.getAmount(), moneda), false);
        }

        if (order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            fila(tabla, "Descuento total (%s%%)".formatted(
                            order.getDiscountPercent().stripTrailingZeros().toPlainString()),
                    "-" + dinero(order.getDiscountAmount(), moneda), false);
            fila(tabla, "Base imponible", dinero(order.getTaxableBase(), moneda), false);
        }

        fila(tabla, "IVA (%s%%)".formatted(order.getTaxRate().stripTrailingZeros().toPlainString()),
                dinero(order.getTaxAmount(), moneda), false);

        fila(tabla, "Envio",
                order.getShippingCost().compareTo(BigDecimal.ZERO) == 0
                        ? "Gratis" : dinero(order.getShippingCost(), moneda), false);

        fila(tabla, "Total", dinero(order.getTotal(), moneda), true);

        doc.add(tabla);
    }

    private void pie(Document doc, Order order) {
        if (order.getPayment() != null) {
            var pago = new Paragraph(
                    "Pago %s mediante %s. Referencia %s.".formatted(
                            order.getPayment().isSimulated() ? "simulado" : "confirmado",
                            order.getPayment().getMethod(),
                            order.getPayment().getReference()),
                    fuente(9, Font.NORMAL, SUAVE));
            pago.setSpacingAfter(6);
            doc.add(pago);
        }

        if (order.getGift() != null && order.getGift().isGift()) {
            doc.add(new Paragraph(
                    "Pedido enviado como regalo: no se incluyo el importe en el paquete.",
                    fuente(9, Font.NORMAL, SUAVE)));
        }

        var nota = new Paragraph(
                "Documento generado automaticamente por " + properties.frontendUrl(),
                fuente(8, Font.NORMAL, SUAVE));
        nota.setSpacingBefore(18);
        doc.add(nota);
    }

    // ------------------------------------------------------------------ utilidades

    private Paragraph etiqueta(String texto) {
        var p = new Paragraph(texto.toUpperCase(Locale.ROOT), fuente(8, Font.NORMAL, SUAVE));
        p.setSpacingAfter(2);
        return p;
    }

    private PdfPCell bloque(String titulo, String cuerpo) {
        var celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPaddingRight(16);
        celda.addElement(etiqueta(titulo));
        var p = new Paragraph(cuerpo, fuente(10, Font.NORMAL, TINTA));
        p.setLeading(14);
        celda.addElement(p);
        return celda;
    }

    private PdfPCell th(String texto, int alineacion) {
        var celda = new PdfPCell(new Phrase(texto.toUpperCase(Locale.ROOT),
                fuente(8, Font.NORMAL, SUAVE)));
        celda.setHorizontalAlignment(alineacion);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(LINEA);
        celda.setBackgroundColor(CREMA);
        celda.setPadding(8);
        return celda;
    }

    private PdfPCell td(String texto, int alineacion) {
        var celda = new PdfPCell(new Phrase(texto, fuente(10, Font.NORMAL, TINTA)));
        celda.setHorizontalAlignment(alineacion);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(LINEA);
        celda.setPadding(8);
        return celda;
    }

    private void fila(PdfPTable tabla, String etiqueta, String valor, boolean destacada) {
        int estilo = destacada ? Font.BOLD : Font.NORMAL;
        int tamano = destacada ? 12 : 10;

        var izquierda = new PdfPCell(new Phrase(etiqueta, fuente(tamano, estilo,
                destacada ? TINTA : SUAVE)));
        izquierda.setBorder(destacada ? Rectangle.TOP : Rectangle.NO_BORDER);
        izquierda.setBorderColor(LINEA);
        izquierda.setPadding(6);

        var derecha = new PdfPCell(new Phrase(valor, fuente(tamano, estilo, TINTA)));
        derecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
        derecha.setBorder(destacada ? Rectangle.TOP : Rectangle.NO_BORDER);
        derecha.setBorderColor(LINEA);
        derecha.setPadding(6);

        tabla.addCell(izquierda);
        tabla.addCell(derecha);
    }

    private Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }

    private String dinero(BigDecimal valor, String moneda) {
        var formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        formato.setMinimumFractionDigits(0);
        formato.setMaximumFractionDigits(2);
        return "%s %s".formatted(formato.format(valor), moneda);
    }
}
