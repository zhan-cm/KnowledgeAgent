package com.zhan.admin;

import com.zhan.admin.dto.AskTrendPoint;
import com.zhan.admin.dto.OverviewStats;
import com.zhan.entity.DocumentStatus;
import com.zhan.repository.AuditLogRepository;
import com.zhan.repository.ConversationRepository;
import com.zhan.repository.DocumentRepository;
import com.zhan.repository.KnowledgeBaseRepository;
import com.zhan.repository.MessageRepository;
import com.zhan.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private KnowledgeBaseRepository kbRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void overviewAggregatesCounts() {
        when(userRepository.count()).thenReturn(2L);
        when(documentRepository.countByStatus(DocumentStatus.INDEXED)).thenReturn(5L);
        when(documentRepository.countByStatus(DocumentStatus.FAILED)).thenReturn(1L);
        when(auditLogRepository.countByActionAndCreatedAtAfter(eq("ASK"), any())).thenReturn(3L);

        OverviewStats stats = adminService.overview();

        assertThat(stats.getUserCount()).isEqualTo(2);
        assertThat(stats.getIndexedDocumentCount()).isEqualTo(5);
        assertThat(stats.getFailedDocumentCount()).isEqualTo(1);
        assertThat(stats.getAskCountToday()).isEqualTo(3);
    }

    @Test
    void askTrendFillsMissingDaysWithZero() {
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{LocalDate.now().minusDays(2).toString(), 4L});
        when(auditLogRepository.countAskByDayLast7Days()).thenReturn(rows);

        List<AskTrendPoint> trend = adminService.askTrend();

        assertThat(trend).hasSize(7);
        assertThat(trend.get(0).getCount()).isZero();
        assertThat(trend.get(0).getDay()).isEqualTo(LocalDate.now().minusDays(6).toString());
        assertThat(trend.get(4).getCount()).isEqualTo(4);
        assertThat(trend.get(4).getDay()).isEqualTo(LocalDate.now().minusDays(2).toString());
    }
}
