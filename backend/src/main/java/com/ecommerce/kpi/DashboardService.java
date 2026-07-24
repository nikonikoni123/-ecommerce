package com.ecommerce.kpi;

import com.ecommerce.company.Department;
import com.ecommerce.company.DepartmentRepository;
import com.ecommerce.kpi.dto.KpiDtos.Dashboard;
import com.ecommerce.kpi.dto.KpiDtos.Series;
import com.ecommerce.kpi.dto.KpiDtos.Slice;
import com.ecommerce.kpi.dto.KpiDtos.StatTile;
import com.ecommerce.order.Order;
import com.ecommerce.order.OrderStatus;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.support.CaseStatus;
import com.ecommerce.support.SupportCase;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Panel de resultados de la empresa.
 *
 * <p>Reune las series que pide la especificacion: ventas general y por mes, casos nuevos, vencidos y
 * en espera, reparto por estado, por departamento, por usuario, y productos mas vendidos. Todo se
 * calcula sobre los pedidos y casos reales de un rango de tiempo; nada esta precalculado.
 */
@Service
public class DashboardService {

    private static final ZoneId ZONA = ZoneId.systemDefault();

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final DepartmentRepository departments;

    public DashboardService(MongoTemplate mongo, UserRepository users,
                            DepartmentRepository departments) {
        this.mongo = mongo;
        this.departments = departments;
        this.users = users;
    }

    public Dashboard build(AppPrincipal actor, int months) {
        String companyId = actor.companyId();
        Instant to = Instant.now();
        Instant from = to.minus((long) months * 31, ChronoUnit.DAYS);

        var pedidos = mongo.find(pedidosPagados(companyId, from, to), Order.class);
        var casos = mongo.find(new Query(Criteria.where("companyId").is(companyId)
                .and("createdAt").gte(from).lte(to)), SupportCase.class);

        return new Dashboard(
                tiles(pedidos, casos, companyId),
                salesByMonth(pedidos, months),
                topProducts(pedidos),
                topCustomers(pedidos),
                casesByStatus(casos),
                byDepartment(companyId, casos),
                byAgent(companyId, casos));
    }

    // ------------------------------------------------------------------ cifras destacadas

    private List<StatTile> tiles(List<Order> pedidos, List<SupportCase> casos, String companyId) {
        BigDecimal ventas = pedidos.stream().map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long entregados = pedidos.stream()
                .filter(o -> o.getStatus() == OrderStatus.ENTREGADO).count();

        long nuevos = casos.stream().filter(c -> c.getStatus() == CaseStatus.ABIERTO).count();
        long vencidos = casos.stream().filter(SupportCase::isOverdue).count();
        long enEspera = casos.stream()
                .filter(c -> c.getStatus() == CaseStatus.ESPERANDO_CLIENTE).count();

        return List.of(
                new StatTile("Ventas", ventas, "CURRENCY"),
                new StatTile("Pedidos entregados", BigDecimal.valueOf(entregados), "COUNT"),
                new StatTile("Casos nuevos", BigDecimal.valueOf(nuevos), "COUNT"),
                new StatTile("Casos vencidos", BigDecimal.valueOf(vencidos), "COUNT"),
                new StatTile("Casos en espera", BigDecimal.valueOf(enEspera), "COUNT"));
    }

    // ------------------------------------------------------------------ series

    private Series salesByMonth(List<Order> pedidos, int months) {
        // Se inicializan los ultimos meses a cero, para que la grafica no tenga huecos.
        var porMes = new LinkedHashMap<String, BigDecimal>();
        YearMonth cursor = YearMonth.now().minusMonths(months - 1L);
        for (int i = 0; i < months; i++) {
            porMes.put(etiquetaMes(cursor), BigDecimal.ZERO);
            cursor = cursor.plusMonths(1);
        }

        for (var o : pedidos) {
            var ym = YearMonth.from(o.getCreatedAt().atZone(ZONA));
            porMes.computeIfPresent(etiquetaMes(ym), (k, v) -> v.add(o.getTotal()));
        }

        return new Series("Ventas por mes", "CURRENCY",
                porMes.entrySet().stream().map(e -> new Slice(e.getKey(), e.getValue())).toList());
    }

    private Series topProducts(List<Order> pedidos) {
        var unidades = new LinkedHashMap<String, Integer>();
        for (var o : pedidos) {
            for (var item : o.getItems()) {
                unidades.merge(item.getName(), item.getQuantity(), Integer::sum);
            }
        }
        var slices = unidades.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> new Slice(e.getKey(), BigDecimal.valueOf(e.getValue())))
                .toList();
        return new Series("Productos mas vendidos", "COUNT", slices);
    }

    private Series casesByStatus(List<SupportCase> casos) {
        var porEstado = new LinkedHashMap<String, Integer>();
        for (var estado : CaseStatus.values()) {
            porEstado.put(estado.getLabel(), 0);
        }
        for (var c : casos) {
            porEstado.merge(c.getStatus().getLabel(), 1, Integer::sum);
        }
        return new Series("Casos por estado", "COUNT",
                porEstado.entrySet().stream()
                        .map(e -> new Slice(e.getKey(), BigDecimal.valueOf(e.getValue())))
                        .toList());
    }

    private Series byDepartment(String companyId, List<SupportCase> casos) {
        var deps = departments.findByCompanyId(companyId);
        var miembros = users.findByCompanyId(companyId);
        // userId -> departmentId
        var deptDeUsuario = new LinkedHashMap<String, String>();
        miembros.forEach(u -> {
            if (u.getDepartmentId() != null) {
                deptDeUsuario.put(u.getId(), u.getDepartmentId());
            }
        });
        var nombreDept = new LinkedHashMap<String, String>();
        deps.forEach(d -> nombreDept.put(d.getId(), d.getName()));

        var conteo = new LinkedHashMap<String, Integer>();
        nombreDept.values().forEach(n -> conteo.put(n, 0));
        conteo.put("Sin departamento", 0);

        for (var c : casos) {
            if (c.getAssignedToUserId() == null) {
                continue;
            }
            String deptId = deptDeUsuario.get(c.getAssignedToUserId());
            String nombre = deptId == null ? "Sin departamento"
                    : nombreDept.getOrDefault(deptId, "Sin departamento");
            conteo.merge(nombre, 1, Integer::sum);
        }

        return new Series("Casos por departamento", "COUNT",
                conteo.entrySet().stream()
                        .map(e -> new Slice(e.getKey(), BigDecimal.valueOf(e.getValue())))
                        .toList());
    }

    private Series byAgent(String companyId, List<SupportCase> casos) {
        var nombre = new LinkedHashMap<String, String>();
        users.findByCompanyId(companyId).forEach(u -> nombre.put(u.getId(), u.displayName().trim()));

        var conteo = new LinkedHashMap<String, Integer>();
        for (var c : casos) {
            if (c.getAssignedToUserId() != null) {
                conteo.merge(nombre.getOrDefault(c.getAssignedToUserId(), "?"), 1, Integer::sum);
            }
        }
        var slices = conteo.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(e -> new Slice(e.getKey(), BigDecimal.valueOf(e.getValue())))
                .toList();
        return new Series("Casos por usuario", "COUNT", new ArrayList<>(slices));
    }

    private Series topCustomers(List<Order> pedidos) {
        var conteo = new LinkedHashMap<String, Integer>();
        for (var o : pedidos) {
            conteo.merge(o.getCustomerName(), 1, Integer::sum);
        }
        var slices = conteo.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5) 
                .map(e -> new Slice(e.getKey(), BigDecimal.valueOf(e.getValue())))
                .toList();
                
        return new Series("Top 5 clientes frecuentes", "COUNT", slices);
    }
    // ------------------------------------------------------------------ apoyo

    private Query pedidosPagados(String companyId, Instant from, Instant to) {
        return new Query(Criteria.where("companyId").is(companyId)
                .and("createdAt").gte(from).lte(to)
                .and("status").nin(List.of(OrderStatus.CANCELADO, OrderStatus.REEMBOLSADO)));
    }

    private String etiquetaMes(YearMonth ym) {
        String[] meses = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct",
                "nov", "dic"};
        return meses[ym.getMonthValue() - 1] + " " + (ym.getYear() % 100);
    }
}
