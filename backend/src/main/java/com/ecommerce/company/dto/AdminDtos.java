package com.ecommerce.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Contratos de la administracion de la empresa: cargos, usuarios, departamentos y actividad. */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ---------------------------------------------------------------- cargos

    public record RoleRequest(
            @NotBlank(message = "El nombre del cargo es obligatorio")
            @Size(max = 80) String name,
            @Size(max = 300) String description,
            /** Permisos que otorga el cargo, por su nombre en el enum Permission. */
            Set<String> permissions) {
    }

    public record RoleView(
            String id,
            String name,
            String description,
            List<String> permissions,
            boolean system,
            int memberCount) {
    }

    /** Un permiso disponible, agrupado, para que la interfaz lo presente por bloques. */
    public record PermissionView(String name, String label, String group) {
    }

    // ---------------------------------------------------------------- usuarios

    public record CreateMemberRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String name,
            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato valido") String email,
            String position,
            String departmentId,
            String managerId,
            Set<String> roleIds) {
    }

    /**
     * Modificacion de un miembro. Todos los campos son opcionales; solo se aplican los presentes.
     * Los permisos individuales van aparte porque son la personalizacion fina sobre los cargos.
     */
    public record UpdateMemberRequest(
            @Size(min = 1, max = 150) String name,
            String position,
            String departmentId,
            String managerId,
            Set<String> roleIds,
            Set<String> extraPermissions,
            Set<String> revokedPermissions) {
    }

    public record MemberView(
            String id,
            String name,
            String email,
            String position,
            boolean root,
            boolean active,
            String departmentId,
            String departmentName,
            String managerId,
            String managerName,
            List<String> roleIds,
            List<String> roleNames,
            List<String> extraPermissions,
            List<String> revokedPermissions,
            /** Permisos efectivos ya resueltos: cargos + concedidos - revocados. */
            List<String> effectivePermissions,
            boolean leadsADepartment,
            Instant createdAt) {
    }

    // ---------------------------------------------------------------- departamentos

    public record DepartmentRequest(
            @NotBlank(message = "El nombre del departamento es obligatorio")
            @Size(max = 100) String name,
            @Size(max = 300) String description,
            /** Jefe que lidera el departamento. Opcional al crear. */
            String leaderUserId) {
    }

    public record DepartmentView(
            String id,
            String name,
            String description,
            String leaderUserId,
            String leaderName,
            int memberCount) {
    }

    // ---------------------------------------------------------------- actividad

    public record ActivityRow(
            String id,
            String userId,
            String userEmail,
            String action,
            String targetType,
            String targetId,
            Instant createdAt) {
    }
}
