package com.ecommerce.company;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.auth.token.VerificationToken;
import com.ecommerce.auth.token.VerificationTokenRepository;
import com.ecommerce.common.ApiException;
import com.ecommerce.company.dto.AdminDtos.CreateMemberRequest;
import com.ecommerce.company.dto.AdminDtos.DepartmentRequest;
import com.ecommerce.company.dto.AdminDtos.DepartmentView;
import com.ecommerce.company.dto.AdminDtos.MemberView;
import com.ecommerce.company.dto.AdminDtos.RoleRequest;
import com.ecommerce.company.dto.AdminDtos.RoleView;
import com.ecommerce.company.dto.AdminDtos.UpdateMemberRequest;
import com.ecommerce.mail.MailService;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.Permission;
import com.ecommerce.security.PermissionResolver;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import com.ecommerce.user.UserStatus;
import com.ecommerce.user.UserType;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administracion de la empresa por parte de root: cargos, usuarios y departamentos.
 *
 * <p>Cada metodo comprueba que el objeto pertenece a la empresa de quien actua, de modo que un root
 * no pueda tocar los datos de otra. La unicidad del propio root la garantiza el indice parcial de
 * Mongo, pero aqui ademas se impide degradarlo o borrarlo por error.
 */
@Service
public class CompanyAdminService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final VerificationTokenRepository tokens;
    private final PermissionResolver permissionResolver;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final ActivityService activityService;

    public CompanyAdminService(UserRepository users, RoleRepository roles,
                               DepartmentRepository departments, VerificationTokenRepository tokens,
                               PermissionResolver permissionResolver, PasswordEncoder passwordEncoder,
                               MailService mailService, ActivityService activityService) {
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.tokens = tokens;
        this.permissionResolver = permissionResolver;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.activityService = activityService;
    }

    // ================================================================= cargos

    public List<RoleView> listRoles(AppPrincipal actor) {
        String companyId = companyOf(actor);
        var miembros = users.findByCompanyId(companyId);
        return roles.findByCompanyId(companyId).stream().map(r -> toRoleView(r, miembros)).toList();
    }

    public RoleView createRole(AppPrincipal actor, RoleRequest request) {
        String companyId = companyOf(actor);
        roles.findByCompanyIdAndNameIgnoreCase(companyId, request.name().trim()).ifPresent(r -> {
            throw ApiException.conflict("ROLE_EXISTS", "Ya existe un cargo con ese nombre.");
        });

        var role = new Role();
        role.setCompanyId(companyId);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setPermissions(parsePermissions(request.permissions()));
        role = roles.save(role);

        activityService.record(actor, "ROLE_CREATE", "Role", role.getId(),
                Map.of("nombre", role.getName()));
        return toRoleView(role, users.findByCompanyId(companyId));
    }

    public RoleView updateRole(AppPrincipal actor, String roleId, RoleRequest request) {
        var role = requireRole(actor, roleId);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setPermissions(parsePermissions(request.permissions()));
        role.setUpdatedAt(Instant.now());
        roles.save(role);

        activityService.record(actor, "ROLE_UPDATE", "Role", role.getId(),
                Map.of("nombre", role.getName()));
        return toRoleView(role, users.findByCompanyId(role.getCompanyId()));
    }

    public void deleteRole(AppPrincipal actor, String roleId) {
        var role = requireRole(actor, roleId);
        if (role.isSystem()) {
            throw ApiException.badRequest("SYSTEM_ROLE", "Los cargos del sistema no se pueden eliminar.");
        }
        // Se quita el cargo de quien lo tuviera, para no dejar referencias colgando.
        var afectados = users.findByCompanyId(role.getCompanyId()).stream()
                .filter(u -> u.getRoleIds().remove(roleId))
                .toList();
        users.saveAll(afectados);
        roles.delete(role);

        activityService.record(actor, "ROLE_DELETE", "Role", roleId, Map.of("nombre", role.getName()));
    }

    // ================================================================= usuarios

    public List<MemberView> listMembers(AppPrincipal actor) {
        String companyId = companyOf(actor);
        var todos = users.findByCompanyId(companyId);
        var deps = departments.findByCompanyId(companyId);
        var todosRoles = roles.findByCompanyId(companyId);
        return todos.stream()
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .map(u -> toMemberView(u, todos, deps, todosRoles))
                .toList();
    }

    public MemberView createMember(AppPrincipal actor, CreateMemberRequest request) {
        String companyId = companyOf(actor);
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo.");
        }

        var member = new User();
        member.setType(UserType.COMPANY_MEMBER);
        member.setEmail(email);
        // Sin contrasena utilizable: el usuario la establece desde el enlace del correo.
        member.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        member.setFirstName(request.name().trim());
        member.setCompanyId(companyId);
        member.setPosition(request.position());
        setDepartment(member, request.departmentId(), companyId);
        setManager(member, request.managerId(), companyId);
        member.setRoleIds(validRoleIds(request.roleIds(), companyId));
        member = users.save(member);

        sendInvite(member);
        activityService.record(actor, "USER_CREATE", "User", member.getId(),
                Map.of("correo", member.getEmail()));

        return toMemberView(member, users.findByCompanyId(companyId),
                departments.findByCompanyId(companyId), roles.findByCompanyId(companyId));
    }

    public MemberView updateMember(AppPrincipal actor, String userId, UpdateMemberRequest request) {
        var member = requireMember(actor, userId);
        String companyId = member.getCompanyId();

        if (request.name() != null) {
            member.setFirstName(request.name().trim());
        }
        if (request.position() != null) {
            member.setPosition(request.position());
        }
        if (request.departmentId() != null) {
            setDepartment(member, request.departmentId().isBlank() ? null : request.departmentId(),
                    companyId);
        }
        if (request.managerId() != null) {
            setManager(member, request.managerId().isBlank() ? null : request.managerId(), companyId);
        }
        if (request.roleIds() != null) {
            member.setRoleIds(validRoleIds(request.roleIds(), companyId));
        }
        if (request.extraPermissions() != null) {
            member.setExtraPermissions(parsePermissions(request.extraPermissions()));
        }
        if (request.revokedPermissions() != null) {
            member.setRevokedPermissions(parsePermissions(request.revokedPermissions()));
        }
        member.setUpdatedAt(Instant.now());
        users.save(member);

        activityService.record(actor, "USER_UPDATE", "User", member.getId(),
                Map.of("correo", member.getEmail()));
        return toMemberView(member, users.findByCompanyId(companyId),
                departments.findByCompanyId(companyId), roles.findByCompanyId(companyId));
    }

    public void deleteMember(AppPrincipal actor, String userId) {
        var member = requireMember(actor, userId);
        if (member.isRoot()) {
            throw ApiException.badRequest("ROOT_PROTECTED",
                    "El usuario root de la empresa no se puede eliminar.");
        }
        if (userId.equals(actor.userId())) {
            throw ApiException.badRequest("SELF_DELETE", "No puedes eliminarte a ti mismo.");
        }

        // Quien tuviera a este usuario como jefe se queda sin jefe, para no dejar referencias rotas.
        var subordinados = users.findByCompanyId(member.getCompanyId()).stream()
                .filter(u -> userId.equals(u.getManagerId()))
                .peek(u -> u.setManagerId(null))
                .toList();
        users.saveAll(subordinados);

        // Si lideraba un departamento, este se queda sin jefe.
        departments.findByCompanyId(member.getCompanyId()).stream()
                .filter(d -> userId.equals(d.getLeaderUserId()))
                .forEach(d -> {
                    d.setLeaderUserId(null);
                    d.setLeaderName(null);
                    departments.save(d);
                });

        member.setStatus(UserStatus.DELETED);
        member.setDeletedAt(Instant.now());
        member.setEmail("eliminado+" + member.getId() + "@ecommerce.local");
        users.save(member);

        activityService.record(actor, "USER_DELETE", "User", userId, Map.of());
    }

    // ================================================================= departamentos

    public List<DepartmentView> listDepartments(AppPrincipal actor) {
        String companyId = companyOf(actor);
        var miembros = users.findByCompanyId(companyId);
        return departments.findByCompanyId(companyId).stream()
                .map(d -> toDepartmentView(d, miembros))
                .toList();
    }

    public DepartmentView createDepartment(AppPrincipal actor, DepartmentRequest request) {
        String companyId = companyOf(actor);
        departments.findByCompanyIdAndNameIgnoreCase(companyId, request.name().trim()).ifPresent(d -> {
            throw ApiException.conflict("DEPT_EXISTS", "Ya existe un departamento con ese nombre.");
        });

        var dept = new Department();
        dept.setCompanyId(companyId);
        dept.setName(request.name().trim());
        dept.setDescription(request.description());
        assignLeader(dept, request.leaderUserId(), companyId);
        dept = departments.save(dept);

        activityService.record(actor, "DEPARTMENT_CREATE", "Department", dept.getId(),
                Map.of("nombre", dept.getName()));
        return toDepartmentView(dept, users.findByCompanyId(companyId));
    }

    public DepartmentView updateDepartment(AppPrincipal actor, String deptId,
                                           DepartmentRequest request) {
        var dept = requireDepartment(actor, deptId);
        dept.setName(request.name().trim());
        dept.setDescription(request.description());
        assignLeader(dept, request.leaderUserId(), dept.getCompanyId());
        dept.setUpdatedAt(Instant.now());
        departments.save(dept);

        activityService.record(actor, "DEPARTMENT_UPDATE", "Department", dept.getId(),
                Map.of("nombre", dept.getName()));
        return toDepartmentView(dept, users.findByCompanyId(dept.getCompanyId()));
    }

    public void deleteDepartment(AppPrincipal actor, String deptId) {
        var dept = requireDepartment(actor, deptId);
        // Los miembros del departamento quedan sin departamento asignado.
        var miembros = users.findByCompanyId(dept.getCompanyId()).stream()
                .filter(u -> deptId.equals(u.getDepartmentId()))
                .peek(u -> u.setDepartmentId(null))
                .toList();
        users.saveAll(miembros);
        departments.delete(dept);

        activityService.record(actor, "DEPARTMENT_DELETE", "Department", deptId,
                Map.of("nombre", dept.getName()));
    }

    // ================================================================= apoyo

    public String companyOf(AppPrincipal actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NOT_A_COMPANY_MEMBER",
                    "Esta seccion es exclusiva de las cuentas de empresa.");
        }
        return actor.companyId();
    }

    private Role requireRole(AppPrincipal actor, String roleId) {
        var role = roles.findById(roleId).orElseThrow(() -> ApiException.notFound("El cargo no existe."));
        if (!role.getCompanyId().equals(companyOf(actor))) {
            throw ApiException.notFound("El cargo no existe en tu empresa.");
        }
        return role;
    }

    private User requireMember(AppPrincipal actor, String userId) {
        var member = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("El usuario no existe."));
        if (!companyOf(actor).equals(member.getCompanyId())) {
            throw ApiException.notFound("El usuario no existe en tu empresa.");
        }
        return member;
    }

    private Department requireDepartment(AppPrincipal actor, String deptId) {
        return departments.findByIdAndCompanyId(deptId, companyOf(actor))
                .orElseThrow(() -> ApiException.notFound("El departamento no existe en tu empresa."));
    }

    private Set<Permission> parsePermissions(Set<String> names) {
        if (names == null) {
            return EnumSet.noneOf(Permission.class);
        }
        var result = EnumSet.noneOf(Permission.class);
        for (var name : names) {
            try {
                result.add(Permission.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_PERMISSION", "Permiso desconocido: " + name);
            }
        }
        return result;
    }

    private Set<String> validRoleIds(Set<String> roleIds, String companyId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        var existentes = roles.findByCompanyId(companyId).stream()
                .map(Role::getId).collect(Collectors.toSet());
        var validos = roleIds.stream().filter(existentes::contains).collect(Collectors.toSet());
        if (validos.size() != roleIds.size()) {
            throw ApiException.badRequest("INVALID_ROLE", "Alguno de los cargos no existe en tu empresa.");
        }
        return new HashSet<>(validos);
    }

    private void setDepartment(User member, String departmentId, String companyId) {
        if (departmentId == null) {
            member.setDepartmentId(null);
            return;
        }
        departments.findByIdAndCompanyId(departmentId, companyId)
                .orElseThrow(() -> ApiException.badRequest("DEPT_NOT_FOUND",
                        "El departamento no existe en tu empresa."));
        member.setDepartmentId(departmentId);
    }

    private void setManager(User member, String managerId, String companyId) {
        if (managerId == null) {
            member.setManagerId(null);
            return;
        }
        if (managerId.equals(member.getId())) {
            throw ApiException.badRequest("SELF_MANAGER", "Un usuario no puede ser su propio jefe.");
        }
        var jefe = users.findById(managerId)
                .filter(u -> companyId.equals(u.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest("MANAGER_NOT_FOUND",
                        "El jefe indicado no existe en tu empresa."));
        // Evita un ciclo directo: el jefe elegido no puede tener a este usuario como su jefe.
        if (member.getId() != null && member.getId().equals(jefe.getManagerId())) {
            throw ApiException.badRequest("MANAGER_CYCLE",
                    "Esa asignacion crearia un ciclo de jefes.");
        }
        member.setManagerId(managerId);
    }

    private void assignLeader(Department dept, String leaderUserId, String companyId) {
        if (leaderUserId == null || leaderUserId.isBlank()) {
            dept.setLeaderUserId(null);
            dept.setLeaderName(null);
            return;
        }
        var jefe = users.findById(leaderUserId)
                .filter(u -> companyId.equals(u.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest("LEADER_NOT_FOUND",
                        "El jefe indicado no existe en tu empresa."));
        dept.setLeaderUserId(jefe.getId());
        dept.setLeaderName(jefe.displayName().trim());
    }

    private void sendInvite(User member) {
        tokens.deleteByUserIdAndPurpose(member.getId(), VerificationToken.Purpose.PASSWORD_RESET);
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        tokens.save(new VerificationToken(member.getId(), token,
                VerificationToken.Purpose.PASSWORD_RESET, Instant.now().plus(INVITE_TTL)));
        mailService.sendPasswordReset(member.getEmail(), member.getFirstName(), token);
    }

    // ------------------------------------------------------------------ vistas

    private RoleView toRoleView(Role role, List<User> miembros) {
        long conElCargo = miembros.stream().filter(u -> u.getRoleIds().contains(role.getId())).count();
        return new RoleView(role.getId(), role.getName(), role.getDescription(),
                role.getPermissions().stream().map(Enum::name).sorted().toList(),
                role.isSystem(), (int) conElCargo);
    }

    private MemberView toMemberView(User u, List<User> todos, List<Department> deps,
                                    List<Role> todosRoles) {
        String deptName = deps.stream()
                .filter(d -> d.getId().equals(u.getDepartmentId()))
                .map(Department::getName).findFirst().orElse(null);
        String managerName = todos.stream()
                .filter(m -> m.getId().equals(u.getManagerId()))
                .map(m -> m.displayName().trim()).findFirst().orElse(null);
        var roleNames = todosRoles.stream()
                .filter(r -> u.getRoleIds().contains(r.getId()))
                .map(Role::getName).toList();
        boolean lidera = deps.stream().anyMatch(d -> u.getId().equals(d.getLeaderUserId()));

        return new MemberView(u.getId(), u.displayName().trim(), u.getEmail(), u.getPosition(),
                u.isRoot(), u.getStatus() == UserStatus.ACTIVE, u.getDepartmentId(), deptName,
                u.getManagerId(), managerName,
                u.getRoleIds().stream().toList(), roleNames,
                u.getExtraPermissions().stream().map(Enum::name).sorted().toList(),
                u.getRevokedPermissions().stream().map(Enum::name).sorted().toList(),
                permissionResolver.resolve(u).stream().map(Enum::name).sorted().toList(),
                lidera, u.getCreatedAt());
    }

    private DepartmentView toDepartmentView(Department d, List<User> miembros) {
        long count = miembros.stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && d.getId().equals(u.getDepartmentId()))
                .count();
        return new DepartmentView(d.getId(), d.getName(), d.getDescription(),
                d.getLeaderUserId(), d.getLeaderName(), (int) count);
    }
}
