package com.ecommerce.chat;

import com.ecommerce.common.ApiException;
import com.ecommerce.common.PageResponse;
import com.ecommerce.company.Department;
import com.ecommerce.company.DepartmentRepository;
import com.ecommerce.notification.Notification;
import com.ecommerce.notification.NotificationService;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.Permission;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Chat general de la empresa y comunicados de root.
 *
 * <p>Reglas de la especificacion:
 * <ul>
 *   <li>El chat general lo <b>lee</b> todo el personal de la empresa, pero solo <b>publican</b>
 *       quienes root autoriza (permiso {@code CHAT_GENERAL_POST}).</li>
 *   <li>Root puede <b>comunicar informacion a toda la empresa</b> ({@code BROADCAST_SEND}); esos
 *       comunicados aparecen en el mismo hilo, destacados, y ademas generan una notificacion a cada
 *       miembro.</li>
 * </ul>
 *
 * <p>Los chats por departamento o equipo llegan en la Fase 5. La lectura se limita al personal de la
 * propia empresa: el chat es interno, no lo ven los clientes.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String CANAL_GENERAL = "general";

    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final NotificationService notifications;

    public ChatService(ChatMessageRepository messages, UserRepository users,
                       DepartmentRepository departments, NotificationService notifications) {
        this.messages = messages;
        this.users = users;
        this.departments = departments;
        this.notifications = notifications;
    }

    public PageResponse<ChatMessageView> history(AppPrincipal principal, int page, int size) {
        String companyId = companyOf(principal);
        var result = messages.findByCompanyIdAndChannelOrderByCreatedAtDesc(
                companyId, CANAL_GENERAL, PageRequest.of(page, size));
        return PageResponse.of(result, m -> toView(m, principal.userId()));
    }

    /** Indica si quien pregunta puede escribir, para que la interfaz muestre o no el cuadro. */
    public boolean canPost(AppPrincipal principal) {
        return principal.has(Permission.CHAT_GENERAL_POST);
    }

    public ChatMessageView post(AppPrincipal principal, String body) {
        String companyId = companyOf(principal);
        // La barrera real esta en el @PreAuthorize del controlador; esto es defensa en profundidad.
        if (!principal.has(Permission.CHAT_GENERAL_POST)) {
            throw ApiException.forbidden("CHAT_NOT_ALLOWED",
                    "No tienes permiso para publicar en el chat general.");
        }

        var autor = users.findById(principal.userId()).orElse(null);
        var mensaje = new ChatMessage(companyId, ChatMessage.Type.MESSAGE, principal.userId(),
                autor == null ? principal.email() : autor.displayName().trim(), body.trim());
        return toView(messages.save(mensaje), principal.userId());
    }

    /**
     * Comunicado de root a toda la empresa. Ademas de quedar en el hilo destacado, notifica a cada
     * miembro: es informacion que la especificacion quiere que llegue a todos, no que dependa de que
     * abran el chat.
     */
    public ChatMessageView broadcast(AppPrincipal principal, String body) {
        String companyId = companyOf(principal);
        var autor = users.findById(principal.userId()).orElse(null);

        var comunicado = new ChatMessage(companyId, ChatMessage.Type.BROADCAST, principal.userId(),
                autor == null ? principal.email() : autor.displayName().trim(), body.trim());
        messages.save(comunicado);

        var miembros = users.findByCompanyId(companyId);
        for (var miembro : miembros) {
            if (!miembro.getId().equals(principal.userId())) {
                notifications.push(miembro.getId(), Notification.Type.BROADCAST,
                        "Comunicado de la empresa", body.trim(), "/empresa/chat");
            }
        }
        log.info("Comunicado enviado a {} miembros de la empresa {}", miembros.size(), companyId);

        return toView(comunicado, principal.userId());
    }

    // ================================================================= chat por departamento

    /** Departamentos a cuyo chat tiene acceso el usuario: el suyo y los que lidera. */
    public List<Department> accessibleDepartments(AppPrincipal principal) {
        String companyId = companyOf(principal);
        var propio = users.findById(principal.userId())
                .map(User::getDepartmentId).orElse(null);

        return departments.findByCompanyId(companyId).stream()
                .filter(d -> principal.root()
                        || d.getId().equals(propio)
                        || principal.userId().equals(d.getLeaderUserId()))
                .toList();
    }

    public PageResponse<ChatMessageView> departmentHistory(AppPrincipal principal, String departmentId,
                                                           int page, int size) {
        requireDepartmentAccess(principal, departmentId);
        var result = messages.findByCompanyIdAndChannelOrderByCreatedAtDesc(
                companyOf(principal), channel(departmentId), PageRequest.of(page, size));
        return PageResponse.of(result, m -> toView(m, principal.userId()));
    }

    /**
     * Publica en el chat de un departamento.
     *
     * <p>A diferencia del chat general, aqui no hace falta un permiso especial para escribir: el
     * chat de equipo es privado y quien tiene acceso puede conversar. La barrera es la pertenencia.
     */
    public ChatMessageView postToDepartment(AppPrincipal principal, String departmentId, String body) {
        requireDepartmentAccess(principal, departmentId);
        var autor = users.findById(principal.userId()).orElse(null);

        var mensaje = new ChatMessage(companyOf(principal), ChatMessage.Type.MESSAGE,
                principal.userId(),
                autor == null ? principal.email() : autor.displayName().trim(), body.trim());
        mensaje.setChannel(channel(departmentId));
        return toView(messages.save(mensaje), principal.userId());
    }

    private void requireDepartmentAccess(AppPrincipal principal, String departmentId) {
        String companyId = companyOf(principal);
        var dept = departments.findByIdAndCompanyId(departmentId, companyId)
                .orElseThrow(() -> ApiException.notFound("El departamento no existe en tu empresa."));

        // Root, el jefe del departamento, o un miembro del propio departamento.
        boolean acceso = principal.root()
                || principal.userId().equals(dept.getLeaderUserId())
                || users.findById(principal.userId())
                        .map(u -> departmentId.equals(u.getDepartmentId()))
                        .orElse(false);
        if (!acceso) {
            throw ApiException.forbidden("NO_DEPARTMENT_ACCESS",
                    "No perteneces a ese departamento ni lo lideras.");
        }
    }

    /** El canal de un departamento se nombra con un prefijo, para separarlo del general. */
    private String channel(String departmentId) {
        return "dept:" + departmentId;
    }

    private String companyOf(AppPrincipal principal) {
        if (principal.companyId() == null) {
            throw ApiException.forbidden("NOT_A_COMPANY_MEMBER",
                    "El chat es exclusivo de las cuentas de empresa.");
        }
        return principal.companyId();
    }

    private ChatMessageView toView(ChatMessage m, String viewerId) {
        return new ChatMessageView(m.getId(), m.getType().name(), m.getAuthorName(), m.getBody(),
                m.getAuthorId().equals(viewerId), m.getCreatedAt());
    }

    /** @param mine el mensaje es del propio usuario, para alinearlo a la derecha en la interfaz */
    public record ChatMessageView(String id, String type, String authorName, String body,
                                  boolean mine, Instant createdAt) {
    }
}
