package com.ecommerce.company;

import com.ecommerce.activity.ActivityService;
import com.ecommerce.auth.dto.AuthDtos.MessageResponse;
import com.ecommerce.common.PageResponse;
import com.ecommerce.company.dto.AdminDtos.CreateMemberRequest;
import com.ecommerce.company.dto.AdminDtos.DepartmentRequest;
import com.ecommerce.company.dto.AdminDtos.DepartmentView;
import com.ecommerce.company.dto.AdminDtos.MemberView;
import com.ecommerce.company.dto.AdminDtos.PermissionView;
import com.ecommerce.company.dto.AdminDtos.RoleRequest;
import com.ecommerce.company.dto.AdminDtos.RoleView;
import com.ecommerce.company.dto.AdminDtos.UpdateMemberRequest;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.security.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administracion de la empresa. Cada bloque exige su permiso, que root reparte entre cargos: gestion
 * de cargos, de usuarios, de departamentos y visor de actividad son competencias separables.
 */
@RestController
@RequestMapping("/api/company/admin")
@Tag(name = "Administracion", description = "Cargos, usuarios, departamentos y actividad")
public class CompanyAdminController {

    private final CompanyAdminService service;
    private final ActivityService activityService;

    public CompanyAdminController(CompanyAdminService service, ActivityService activityService) {
        this.service = service;
        this.activityService = activityService;
    }

    // ---------------------------------------------------------------- catalogo de permisos

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE','USER_MANAGE')")
    @Operation(summary = "Permisos disponibles, agrupados, para armar cargos y accesos")
    public List<PermissionView> permissions() {
        return Arrays.stream(Permission.values())
                .map(p -> new PermissionView(p.name(), p.getLabel(), p.getGroup().name()))
                .toList();
    }

    // ---------------------------------------------------------------- cargos

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Cargos de la empresa")
    public List<RoleView> roles(@AuthenticationPrincipal AppPrincipal actor) {
        return service.listRoles(actor);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Crear un cargo (plantilla de permisos)")
    public RoleView createRole(@AuthenticationPrincipal AppPrincipal actor,
                               @Valid @RequestBody RoleRequest request) {
        return service.createRole(actor, request);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Modificar un cargo")
    public RoleView updateRole(@AuthenticationPrincipal AppPrincipal actor, @PathVariable String id,
                               @Valid @RequestBody RoleRequest request) {
        return service.updateRole(actor, id, request);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Eliminar un cargo")
    public MessageResponse deleteRole(@AuthenticationPrincipal AppPrincipal actor,
                                      @PathVariable String id) {
        service.deleteRole(actor, id);
        return new MessageResponse("Cargo eliminado.");
    }

    // ---------------------------------------------------------------- usuarios

    @GetMapping("/members")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Usuarios de la empresa, con sus permisos efectivos")
    public List<MemberView> members(@AuthenticationPrincipal AppPrincipal actor) {
        return service.listMembers(actor);
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Crear un usuario de la empresa e invitarlo por correo")
    public MemberView createMember(@AuthenticationPrincipal AppPrincipal actor,
                                   @Valid @RequestBody CreateMemberRequest request) {
        return service.createMember(actor, request);
    }

    @PatchMapping("/members/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Modificar un usuario: cargos, permisos, departamento y jefe")
    public MemberView updateMember(@AuthenticationPrincipal AppPrincipal actor, @PathVariable String id,
                                   @Valid @RequestBody UpdateMemberRequest request) {
        return service.updateMember(actor, id, request);
    }

    @DeleteMapping("/members/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Eliminar un usuario de la empresa")
    public MessageResponse deleteMember(@AuthenticationPrincipal AppPrincipal actor,
                                        @PathVariable String id) {
        service.deleteMember(actor, id);
        return new MessageResponse("Usuario eliminado.");
    }

    // ---------------------------------------------------------------- departamentos

    @GetMapping("/departments")
    @PreAuthorize("hasAnyAuthority('DEPARTMENT_MANAGE','USER_MANAGE')")
    @Operation(summary = "Departamentos de la empresa")
    public List<DepartmentView> departments(@AuthenticationPrincipal AppPrincipal actor) {
        return service.listDepartments(actor);
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    @Operation(summary = "Crear un departamento y asignarle un jefe")
    public DepartmentView createDepartment(@AuthenticationPrincipal AppPrincipal actor,
                                           @Valid @RequestBody DepartmentRequest request) {
        return service.createDepartment(actor, request);
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    @Operation(summary = "Modificar un departamento")
    public DepartmentView updateDepartment(@AuthenticationPrincipal AppPrincipal actor,
                                           @PathVariable String id,
                                           @Valid @RequestBody DepartmentRequest request) {
        return service.updateDepartment(actor, id, request);
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    @Operation(summary = "Eliminar un departamento")
    public MessageResponse deleteDepartment(@AuthenticationPrincipal AppPrincipal actor,
                                            @PathVariable String id) {
        service.deleteDepartment(actor, id);
        return new MessageResponse("Departamento eliminado.");
    }

    // ---------------------------------------------------------------- actividad

    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('ACTIVITY_VIEW')")
    @Operation(summary = "Actividad de los usuarios de la empresa")
    public PageResponse<ActivityService.ActivityView> activity(
            @AuthenticationPrincipal AppPrincipal actor,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return activityService.list(service.companyOf(actor), userId, Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
    }
}
