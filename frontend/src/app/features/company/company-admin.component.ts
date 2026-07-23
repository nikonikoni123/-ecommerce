import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../../core/api-error';
import { formatDate } from '../../core/format';
import {
  DepartmentView,
  MemberView,
  PermissionView,
  RoleView,
} from '../../core/models';
import {
  CompanyAdminService,
  CreateMemberPayload,
  DepartmentPayload,
  RolePayload,
  UpdateMemberPayload,
} from '../../core/admin.service';
import { AlertComponent } from '../../shared/alert.component';

type Tab = 'cargos' | 'usuarios' | 'departamentos';

/** Un grupo de permisos, para presentarlos en bloques al armar cargos o accesos. */
interface PermissionGroup {
  name: string;
  permissions: PermissionView[];
}

@Component({
  selector: 'app-company-admin',
  imports: [FormsModule, AlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-admin.component.html',
  styleUrl: './company-admin.component.scss',
})
export class CompanyAdminComponent {
  private readonly api = inject(CompanyAdminService);

  protected readonly tab = signal<Tab>('usuarios');
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);

  // --- catalogos ---
  protected readonly permissions = signal<PermissionView[]>([]);
  protected readonly roles = signal<RoleView[]>([]);
  protected readonly members = signal<MemberView[]>([]);
  protected readonly departments = signal<DepartmentView[]>([]);

  protected readonly date = formatDate;

  protected readonly groups = computed<PermissionGroup[]>(() => {
    const byGroup = new Map<string, PermissionView[]>();
    for (const p of this.permissions()) {
      const list = byGroup.get(p.group) ?? [];
      list.push(p);
      byGroup.set(p.group, list);
    }
    return [...byGroup.entries()].map(([name, permissions]) => ({ name, permissions }));
  });

  // ================================================================= cargos: edicion
  protected readonly roleForm = signal<RolePayload & { id: string | null }>(this.emptyRole());
  protected readonly editingRole = computed(() => this.roleForm().id !== null);

  // ================================================================= usuarios: edicion
  protected readonly memberDraft = signal<CreateMemberPayload>(this.emptyMember());
  /** Usuario que se esta editando en el panel inferior, o null si se esta creando uno nuevo. */
  protected readonly editingMember = signal<MemberView | null>(null);

  // ================================================================= departamentos: edicion
  protected readonly deptForm = signal<DepartmentPayload & { id: string | null }>(this.emptyDept());
  protected readonly editingDept = computed(() => this.deptForm().id !== null);

  constructor() {
    this.api.permissions().subscribe({
      next: (p) => this.permissions.set(p),
      error: () => undefined,
    });
    this.reloadRoles();
    this.reloadMembers();
    this.reloadDepartments();
  }

  protected switchTab(tab: Tab): void {
    this.tab.set(tab);
    this.error.set(null);
    this.notice.set(null);
  }

  // ------------------------------------------------------------------ cargas
  private reloadRoles(): void {
    this.api.roles().subscribe({ next: (r) => this.roles.set(r), error: () => undefined });
  }

  private reloadMembers(): void {
    this.api.members().subscribe({ next: (m) => this.members.set(m), error: () => undefined });
  }

  private reloadDepartments(): void {
    this.api.departments().subscribe({
      next: (d) => this.departments.set(d),
      error: () => undefined,
    });
  }

  // ================================================================= cargos
  protected editRole(role: RoleView): void {
    this.roleForm.set({
      id: role.id,
      name: role.name,
      description: role.description ?? '',
      permissions: [...role.permissions],
    });
    this.notice.set(null);
  }

  protected newRole(): void {
    this.roleForm.set(this.emptyRole());
  }

  protected toggleRolePermission(name: string): void {
    this.roleForm.update((f) => ({
      ...f,
      permissions: f.permissions.includes(name)
        ? f.permissions.filter((p) => p !== name)
        : [...f.permissions, name],
    }));
  }

  protected roleHas(name: string): boolean {
    return this.roleForm().permissions.includes(name);
  }

  protected saveRole(): void {
    const form = this.roleForm();
    if (!form.name.trim()) {
      this.error.set('El cargo necesita un nombre.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const payload: RolePayload = {
      name: form.name.trim(),
      description: form.description,
      permissions: form.permissions,
    };
    const req = form.id
      ? this.api.updateRole(form.id, payload)
      : this.api.createRole(payload);
    req.subscribe({
      next: () => {
        this.notice.set(form.id ? 'Cargo actualizado.' : 'Cargo creado.');
        this.roleForm.set(this.emptyRole());
        this.reloadRoles();
        this.reloadMembers();
        this.busy.set(false);
      },
      error: (err) => this.fail(err, 'No pudimos guardar el cargo.'),
    });
  }

  protected deleteRole(role: RoleView): void {
    if (!confirm(`Eliminar el cargo "${role.name}"? Se retirara de ${role.memberCount} usuario(s).`)) {
      return;
    }
    this.api.deleteRole(role.id).subscribe({
      next: () => {
        this.notice.set('Cargo eliminado.');
        this.reloadRoles();
        this.reloadMembers();
      },
      error: (err) => this.error.set(errorMessage(err, 'No pudimos eliminar el cargo.')),
    });
  }

  // ================================================================= usuarios
  protected newMember(): void {
    this.editingMember.set(null);
    this.memberDraft.set(this.emptyMember());
    this.notice.set(null);
  }

  protected editMember(member: MemberView): void {
    this.editingMember.set(member);
    this.notice.set(null);
  }

  protected toggleDraftRole(id: string): void {
    this.memberDraft.update((d) => ({
      ...d,
      roleIds: d.roleIds.includes(id)
        ? d.roleIds.filter((r) => r !== id)
        : [...d.roleIds, id],
    }));
  }

  protected createMember(): void {
    const draft = this.memberDraft();
    if (!draft.name.trim() || !draft.email.trim()) {
      this.error.set('El usuario necesita nombre y correo.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.createMember(draft).subscribe({
      next: () => {
        this.notice.set('Usuario creado. Le enviamos una invitacion por correo.');
        this.memberDraft.set(this.emptyMember());
        this.reloadMembers();
        this.busy.set(false);
      },
      error: (err) => this.fail(err, 'No pudimos crear el usuario.'),
    });
  }

  /** Guarda cambios del usuario en edicion (cargos, departamento, jefe, permisos, cargo/puesto). */
  protected saveMember(patch: UpdateMemberPayload): void {
    const member = this.editingMember();
    if (!member) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.updateMember(member.id, patch).subscribe({
      next: (updated) => {
        this.notice.set('Usuario actualizado.');
        this.editingMember.set(updated);
        this.reloadMembers();
        this.busy.set(false);
      },
      error: (err) => this.fail(err, 'No pudimos actualizar el usuario.'),
    });
  }

  protected setMemberDepartment(value: string): void {
    this.saveMember({ departmentId: value });
  }

  protected setMemberManager(value: string): void {
    this.saveMember({ managerId: value });
  }

  protected toggleMemberRole(member: MemberView, id: string): void {
    const roleIds = member.roleIds.includes(id)
      ? member.roleIds.filter((r) => r !== id)
      : [...member.roleIds, id];
    this.saveMember({ roleIds });
  }

  protected toggleMemberPermission(member: MemberView, name: string, kind: 'extra' | 'revoked'): void {
    const source = kind === 'extra' ? member.extraPermissions : member.revokedPermissions;
    const next = source.includes(name)
      ? source.filter((p) => p !== name)
      : [...source, name];
    this.saveMember(kind === 'extra' ? { extraPermissions: next } : { revokedPermissions: next });
  }

  protected deleteMember(member: MemberView): void {
    if (!confirm(`Eliminar a ${member.name}? Su cuenta quedara deshabilitada.`)) {
      return;
    }
    this.api.deleteMember(member.id).subscribe({
      next: () => {
        this.notice.set('Usuario eliminado.');
        this.editingMember.set(null);
        this.reloadMembers();
      },
      error: (err) => this.error.set(errorMessage(err, 'No pudimos eliminar el usuario.')),
    });
  }

  // ================================================================= departamentos
  protected newDept(): void {
    this.deptForm.set(this.emptyDept());
    this.notice.set(null);
  }

  protected editDept(dept: DepartmentView): void {
    this.deptForm.set({
      id: dept.id,
      name: dept.name,
      description: dept.description ?? '',
      leaderUserId: dept.leaderUserId ?? '',
    });
    this.notice.set(null);
  }

  protected saveDept(): void {
    const form = this.deptForm();
    if (!form.name.trim()) {
      this.error.set('El departamento necesita un nombre.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const payload: DepartmentPayload = {
      name: form.name.trim(),
      description: form.description,
      leaderUserId: form.leaderUserId || null,
    };
    const req = form.id
      ? this.api.updateDepartment(form.id, payload)
      : this.api.createDepartment(payload);
    req.subscribe({
      next: () => {
        this.notice.set(form.id ? 'Departamento actualizado.' : 'Departamento creado.');
        this.deptForm.set(this.emptyDept());
        this.reloadDepartments();
        this.reloadMembers();
        this.busy.set(false);
      },
      error: (err) => this.fail(err, 'No pudimos guardar el departamento.'),
    });
  }

  protected deleteDept(dept: DepartmentView): void {
    if (!confirm(`Eliminar el departamento "${dept.name}"? Sus miembros quedaran sin departamento.`)) {
      return;
    }
    this.api.deleteDepartment(dept.id).subscribe({
      next: () => {
        this.notice.set('Departamento eliminado.');
        this.reloadDepartments();
        this.reloadMembers();
      },
      error: (err) => this.error.set(errorMessage(err, 'No pudimos eliminar el departamento.')),
    });
  }

  // ------------------------------------------------------------------ apoyo
  private fail(err: unknown, fallback: string): void {
    this.error.set(errorMessage(err, fallback));
    this.busy.set(false);
  }

  private emptyRole(): RolePayload & { id: string | null } {
    return { id: null, name: '', description: '', permissions: [] };
  }

  private emptyMember(): CreateMemberPayload {
    return { name: '', email: '', position: '', departmentId: '', managerId: '', roleIds: [] };
  }

  private emptyDept(): DepartmentPayload & { id: string | null } {
    return { id: null, name: '', description: '', leaderUserId: '' };
  }
}
