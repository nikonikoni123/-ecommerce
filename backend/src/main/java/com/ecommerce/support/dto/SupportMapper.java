package com.ecommerce.support.dto;

import com.ecommerce.support.CaseStatus;
import com.ecommerce.support.SupportCase;
import com.ecommerce.support.dto.SupportDtos.CaseDetail;
import com.ecommerce.support.dto.SupportDtos.CaseMessageView;
import com.ecommerce.support.dto.SupportDtos.CaseRow;

public final class SupportMapper {

    private SupportMapper() {
    }

    public static CaseRow toRow(SupportCase c) {
        return new CaseRow(
                c.getId(), c.getNumber(), c.getSubject(),
                c.getStatus().name(), c.getStatus().getLabel(), c.getStatus().isTerminal(),
                c.getPriority(), c.getSentiment(), c.isAiClassified(),
                c.getDueDate(), c.isOverdue(),
                c.getCustomerName(), c.getCompanyName(), c.getOrderNumber(), c.getAssignedToName(),
                c.getMessages().size(), c.getLastMessageAt(), c.getCreatedAt());
    }

    public static CaseDetail toDetail(SupportCase c) {
        return new CaseDetail(
                c.getId(), c.getNumber(), c.getSubject(),
                c.getStatus().name(), c.getStatus().getLabel(), c.getStatus().isTerminal(),
                c.getPriority(), c.getSentiment(), c.getAiReason(), c.isAiClassified(),
                c.getDueDate(), c.isOverdue(),
                c.getCustomerName(), c.getCustomerEmail(), c.getCompanyName(),
                c.getOrderId(), c.getOrderNumber(), c.getAssignedToName(),
                c.getStatus().companyTransitions().stream().map(Enum::name).sorted().toList(),
                c.getMessages().stream().map(SupportMapper::toMessage).toList(),
                c.getCreatedAt());
    }

    private static CaseMessageView toMessage(SupportCase.CaseMessage m) {
        return new CaseMessageView(m.getAuthor().name(), m.getAuthorName(), m.getBody(),
                m.getCreatedAt());
    }
}
