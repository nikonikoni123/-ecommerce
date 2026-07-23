package com.ecommerce.company;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Departamento o equipo de una empresa.
 *
 * <p>Root los crea a medida y les asigna un jefe. La pertenencia de cada persona vive en su propio
 * documento ({@code User.departmentId}), no en una lista aqui: asi mover a alguien de equipo es una
 * sola escritura y no hay dos copias que sincronizar.
 *
 * <p>La jerarquia "un jefe puede tener jefe" no se modela en el departamento, sino en el
 * {@code managerId} autorreferencial del usuario, que es mas flexible que atarla al organigrama.
 */
@Document("departments")
public class Department {

    @Id
    private String id;

    private String companyId;
    private String name;
    private String description;

    /** Jefe que lidera el departamento. Debe ser un usuario de la misma empresa. */
    private String leaderUserId;
    private String leaderName;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLeaderUserId() {
        return leaderUserId;
    }

    public void setLeaderUserId(String leaderUserId) {
        this.leaderUserId = leaderUserId;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
