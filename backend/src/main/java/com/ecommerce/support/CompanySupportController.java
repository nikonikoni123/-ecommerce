package com.ecommerce.support;

import com.ecommerce.common.PageResponse;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.support.dto.SupportDtos.AssignRequest;
import com.ecommerce.support.dto.SupportDtos.CaseDetail;
import com.ecommerce.support.dto.SupportDtos.CaseRow;
import com.ecommerce.support.dto.SupportDtos.CaseStats;
import com.ecommerce.support.dto.SupportDtos.ChangeCaseStatusRequest;
import com.ecommerce.support.dto.SupportDtos.PostMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pestana de atencion a casos de la empresa. Cada operacion exige su permiso, de modo que root pueda
 * separar quien ve los casos de quien los responde.
 */
@RestController
@RequestMapping("/api/company/cases")
@Tag(name = "Atencion de casos", description = "Bandeja de casos priorizada por BERT")
public class CompanySupportController {

    private final CompanySupportService service;

    public CompanySupportController(CompanySupportService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CASE_VIEW')")
    @Operation(summary = "Bandeja de casos, ordenada por prioridad, fecha o vencimiento")
    public PageResponse<CaseRow> inbox(@AuthenticationPrincipal AppPrincipal principal,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.inbox(principal, status, sort, Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('CASE_VIEW')")
    @Operation(summary = "Contadores: nuevos, en atencion, vencidos, en espera y resueltos")
    public CaseStats stats(@AuthenticationPrincipal AppPrincipal principal) {
        return service.stats(principal);
    }

    @GetMapping("/statuses")
    @PreAuthorize("hasAuthority('CASE_VIEW')")
    @Operation(summary = "Estados de caso y sus transiciones")
    public List<StatusOption> statuses() {
        return service.allStatuses().stream()
                .map(s -> new StatusOption(s.name(), s.getLabel(), s.isTerminal(),
                        s.companyTransitions().stream().map(Enum::name).sorted().toList()))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CASE_VIEW')")
    @Operation(summary = "Ver un caso y su conversacion")
    public CaseDetail detail(@AuthenticationPrincipal AppPrincipal principal,
                             @PathVariable String id) {
        return service.detail(principal, id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('CASE_ASSIGN')")
    @Operation(summary = "Asignar el caso a un agente (o a uno mismo)")
    public CaseDetail assign(@AuthenticationPrincipal AppPrincipal principal,
                             @PathVariable String id,
                             @RequestBody(required = false) AssignRequest request) {
        return service.assign(principal, id, request == null ? null : request.agentUserId());
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasAuthority('CASE_REPLY')")
    @Operation(summary = "Responder al cliente en el caso")
    public CaseDetail reply(@AuthenticationPrincipal AppPrincipal principal,
                            @PathVariable String id,
                            @Valid @RequestBody PostMessageRequest request) {
        return service.reply(principal, id, request.message());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CASE_REPLY')")
    @Operation(summary = "Cambiar el estado del caso")
    public CaseDetail changeStatus(@AuthenticationPrincipal AppPrincipal principal,
                                   @PathVariable String id,
                                   @Valid @RequestBody ChangeCaseStatusRequest request) {
        return service.changeStatus(principal, id, request.status());
    }

    public record StatusOption(String status, String label, boolean terminal,
                               List<String> allowedTransitions) {
    }
}
