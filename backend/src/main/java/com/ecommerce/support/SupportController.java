package com.ecommerce.support;

import com.ecommerce.common.PageResponse;
import com.ecommerce.security.AppPrincipal;
import com.ecommerce.support.dto.SupportDtos.CaseDetail;
import com.ecommerce.support.dto.SupportDtos.CaseRow;
import com.ecommerce.support.dto.SupportDtos.OpenCaseRequest;
import com.ecommerce.support.dto.SupportDtos.PostMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Casos de atencion del cliente: abrir, listar, ver el hilo y responder. Resuelve el requisito de
 * "realizar comentarios o peticiones al vendedor".
 */
@RestController
@RequestMapping("/api/cases")
@Tag(name = "Casos de atencion", description = "Comentarios y peticiones del cliente al vendedor")
public class SupportController {

    private final SupportCaseService service;

    public SupportController(SupportCaseService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abrir un caso sobre un pedido")
    public CaseDetail open(@AuthenticationPrincipal AppPrincipal principal,
                           @Valid @RequestBody OpenCaseRequest request) {
        return service.open(principal, request.subject(), request.message(), request.orderId());
    }

    @GetMapping
    @Operation(summary = "Mis casos, del mas reciente al mas antiguo")
    public PageResponse<CaseRow> myCases(@AuthenticationPrincipal AppPrincipal principal,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.myCases(principal, Math.max(page, 0), Math.min(Math.max(size, 1), 50));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un caso y su conversacion")
    public CaseDetail detail(@AuthenticationPrincipal AppPrincipal principal,
                             @PathVariable String id) {
        return service.myCase(principal, id);
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Responder en un caso")
    public CaseDetail addMessage(@AuthenticationPrincipal AppPrincipal principal,
                                 @PathVariable String id,
                                 @Valid @RequestBody PostMessageRequest request) {
        return service.addMessage(principal, id, request.message());
    }
}
