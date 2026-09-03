package com.moriah.skillhub.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.assessment.dto.AddBankQuestionRequest;
import com.moriah.skillhub.assessment.dto.CreateQuestionBankRequest;
import com.moriah.skillhub.assessment.dto.QuestionBankItemResponse;
import com.moriah.skillhub.assessment.dto.QuestionBankResponse;
import com.moriah.skillhub.assessment.dto.UpdateQuestionBankRequest;
import com.moriah.skillhub.assessment.entity.QuestionBank;
import com.moriah.skillhub.assessment.entity.QuestionBankItem;
import com.moriah.skillhub.assessment.entity.QuestionDifficulty;
import com.moriah.skillhub.assessment.entity.QuestionType;
import com.moriah.skillhub.assessment.repository.QuestionBankItemRepository;
import com.moriah.skillhub.assessment.repository.QuestionBankRepository;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    @Mock
    private QuestionBankRepository bankRepository;
    @Mock
    private QuestionBankItemRepository itemRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private QuestionBankService service;

    private QuestionBank bank(long id) {
        QuestionBank b = new QuestionBank();
        b.setId(id);
        b.setName("Java Core");
        b.setTopic("java");
        b.setCreatedBy(4L);
        b.setActive(true);
        return b;
    }

    @Test
    void createBank_startsActiveWithZeroQuestions() {
        when(bankRepository.save(any(QuestionBank.class))).thenAnswer(inv -> {
            QuestionBank b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });
        when(userRepository.findAllById(List.of(4L))).thenReturn(List.of(userWith(4L, "tr-uuid")));

        QuestionBankResponse response = service.createBank(
                new CreateQuestionBankRequest("Java Core", "java", "  "), 4L);

        assertThat(response.active()).isTrue();
        assertThat(response.questionCount()).isZero();
        assertThat(response.createdByUuid()).isEqualTo("tr-uuid");
    }

    @Test
    void addQuestion_mcq_persistsOptionsAndKeyAsJson() {
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(1L)));
        when(itemRepository.save(any(QuestionBankItem.class))).thenAnswer(inv -> {
            QuestionBankItem i = inv.getArgument(0);
            i.setId(10L);
            return i;
        });
        when(userRepository.findById(4L)).thenReturn(Optional.of(userWith(4L, "tr-uuid")));

        QuestionBankItemResponse response = service.addQuestion(1L, new AddBankQuestionRequest(
                "2 + 2 = ?", QuestionType.MCQ, List.of("3", "4", "5"), List.of(1), 2, "Basic arithmetic",
                QuestionDifficulty.EASY), 4L);

        ArgumentCaptor<QuestionBankItem> captor = ArgumentCaptor.forClass(QuestionBankItem.class);
        org.mockito.Mockito.verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getOptions()).isEqualTo("[\"3\",\"4\",\"5\"]");
        assertThat(captor.getValue().getCorrectAnswer()).isEqualTo("[1]");
        assertThat(captor.getValue().getDifficulty()).isEqualTo(QuestionDifficulty.EASY);
        // correctAnswer is never serialized into the response
        assertThat(response.options()).containsExactly("3", "4", "5");
    }

    @Test
    void addQuestion_unknownBank_throwsNotFound() {
        when(bankRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addQuestion(404L, new AddBankQuestionRequest(
                "q", QuestionType.CODE, null, null, 5, null, null), 4L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listQuestions_unknownBank_throwsNotFound() {
        when(bankRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.listQuestions(404L, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listBanks_countsQuestionsPerBank() {
        when(bankRepository.search(eq("java"), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bank(1L)), PageRequest.of(0, 20), 1));
        when(itemRepository.countByBankId(1L)).thenReturn(7L);
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<QuestionBankResponse> page = service.listBanks("java", true, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).questionCount()).isEqualTo(7L);
    }

    @Test
    void addBankQuestionRequest_rejectsCodeWithOptionsAtConstruction() {
        assertThatThrownBy(() -> new AddBankQuestionRequest(
                "q", QuestionType.CODE, List.of("a", "b"), null, 5, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateBank_replacesFields() {
        QuestionBank b = bank(3L);
        when(bankRepository.findById(3L)).thenReturn(Optional.of(b));
        when(itemRepository.countByBankId(3L)).thenReturn(2L);
        when(userRepository.findAllById(any())).thenReturn(List.of());

        QuestionBankResponse res = service.updateBank(3L,
                new UpdateQuestionBankRequest("Java Advanced", "java-advanced", "harder set", false));

        assertThat(b.getName()).isEqualTo("Java Advanced");
        assertThat(b.getTopic()).isEqualTo("java-advanced");
        assertThat(b.isActive()).isFalse();
        assertThat(res.questionCount()).isEqualTo(2L);
    }

    @Test
    void deactivateBank_flipsActive() {
        QuestionBank b = bank(3L);
        when(bankRepository.findById(3L)).thenReturn(Optional.of(b));

        service.deactivateBank(3L);

        assertThat(b.isActive()).isFalse();
    }

    @Test
    void removeQuestion_deletesWhenItemBelongsToBank() {
        QuestionBankItem item = new QuestionBankItem();
        item.setId(10L);
        item.setBank(bank(3L));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        service.removeQuestion(3L, 10L);

        org.mockito.Mockito.verify(itemRepository).delete(item);
    }

    @Test
    void removeQuestion_wrongBank_throwsNotFound() {
        QuestionBankItem item = new QuestionBankItem();
        item.setId(10L);
        item.setBank(bank(99L));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.removeQuestion(3L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User userWith(long id, String uuid) {
        User u = new User();
        u.setId(id);
        u.setUuid(uuid);
        return u;
    }
}
