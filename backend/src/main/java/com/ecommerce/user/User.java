package com.ecommerce.user;

import com.ecommerce.security.Permission;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento unico para los dos tipos de cuenta, discriminado por {@link UserType}.
 *
 * <p>Los campos de cliente y los de miembro de empresa conviven en el mismo documento y solo se
 * rellenan los del tipo correspondiente. Mongo no penaliza los campos ausentes, y esto mantiene un
 * unico flujo de autenticacion para ambos tipos.
 */
@Document("users")
public class User {

    @Id
    private String id;

    private UserType type;

    // --- Campos comunes ---
    private String email;
    private String passwordHash;
    private String phone;
    private String address;
    private String postalCode;
    private boolean emailVerified;
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Verificacion en dos pasos. Viene desactivada por defecto: mientras {@code twoFactorEnabled}
     * sea falso el login devuelve un recordatorio para que el usuario la active.
     */
    private boolean twoFactorEnabled;

    /** Secreto TOTP en base32. Se genera en el setup y solo se confirma al validar un codigo. */
    private String twoFactorSecret;

    /** Secreto generado pero todavia no confirmado con un codigo valido. */
    private boolean twoFactorPending;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant deletedAt;

    // --- Solo CUSTOMER ---
    private String firstName;
    private String lastName;
    private String username;
    private String gender;

    /**
     * Afinidad del cliente con las etiquetas invisibles de los productos. Se incrementa cuando el
     * cliente interactua con un producto y se usa para priorizar el catalogo. Nunca se expone.
     */
    private Map<String, Double> tagAffinity = new HashMap<>();

    // --- Solo COMPANY_MEMBER ---
    private String companyId;

    /** Unico por empresa. Tiene todos los permisos de forma implicita. */
    private boolean root;

    private String departmentId;

    /** Jefe directo. Autorreferencial: un jefe puede a su vez tener jefe. */
    private String managerId;

    private String position;

    /** Cargos asignados. Cada cargo es una plantilla de permisos. */
    private Set<String> roleIds = new HashSet<>();

    /** Permisos concedidos individualmente por root, por encima de los cargos. */
    private Set<Permission> extraPermissions = new HashSet<>();

    /** Permisos retirados individualmente por root, aunque los otorgue un cargo. */
    private Set<Permission> revokedPermissions = new HashSet<>();

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }

    public String displayName() {
        if (type == UserType.CUSTOMER) {
            return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
        }
        return firstName != null ? firstName : email;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserType getType() {
        return type;
    }

    public void setType(UserType type) {
        this.type = type;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
    }

    public boolean isTwoFactorPending() {
        return twoFactorPending;
    }

    public void setTwoFactorPending(boolean twoFactorPending) {
        this.twoFactorPending = twoFactorPending;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Map<String, Double> getTagAffinity() {
        return tagAffinity;
    }

    public void setTagAffinity(Map<String, Double> tagAffinity) {
        this.tagAffinity = tagAffinity == null ? new HashMap<>() : tagAffinity;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public boolean isRoot() {
        return root;
    }

    public void setRoot(boolean root) {
        this.root = root;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Set<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<String> roleIds) {
        this.roleIds = roleIds == null ? new HashSet<>() : roleIds;
    }

    public Set<Permission> getExtraPermissions() {
        return extraPermissions;
    }

    public void setExtraPermissions(Set<Permission> extraPermissions) {
        this.extraPermissions = extraPermissions == null ? new HashSet<>() : extraPermissions;
    }

    public Set<Permission> getRevokedPermissions() {
        return revokedPermissions;
    }

    public void setRevokedPermissions(Set<Permission> revokedPermissions) {
        this.revokedPermissions = revokedPermissions == null ? new HashSet<>() : revokedPermissions;
    }
}
