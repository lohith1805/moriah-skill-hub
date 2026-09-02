package com.moriah.skillhub.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.moriah.skillhub.assessment.dto.AddBankQuestionRequest;
import com.moriah.skillhub.assessment.dto.CreateQuestionBankRequest;
import com.moriah.skillhub.assessment.dto.QuestionBankItemResponse;
import com.moriah.skillhub.assessment.dto.QuestionBankResponse;
import com.moriah.skillhub.assessment.entity.QuestionBank;
import com.moriah.skillhub.assessment.entity.QuestionBankItem;
import com.moriah.skillhub.assessment.repository.QuestionBankItemRepository;
import com.moriah.skillhub.assessment.repository.QuestionBankRepository;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assessment question bank (gap B1.15). A reusable question pool alongside feature 14's inline
 * quiz questions. TRAINER_PM/ADMIN (gated on {@link QuestionBankController}). {@code options}/
 * {@code correctAnswer} round-trip through {@code ObjectMapper} exactly as {@code QuizService}
 * handles {@code QuizQuestion} — and {@code correctAnswer} is never serialized into a response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionBankService {

    private final QuestionBankRepository bankRepository;
    private final QuestionBankItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuestionBankResponse createBank(CreateQuestionBankRequest request, Long callerUserId) {
        QuestionBank bank = new QuestionBank();
        bank.setName(request.name());
        bank.setTopic(request.topic());
        bank.setDescription(blankToNull(request.description()));
        bank.setCreatedBy(callerUserId);
        bank.setActive(true);
        bankRepository.save(bank);
        log.info("[assessments/banks] {} created bank {} ({})", callerUserId, bank.getId(), bank.getTopic());
        return toBankResponse(bank, 0L, creatorUuids(List.of(bank)));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionBankResponse> listBanks(String topic, Boolean active, Pageable pageable) {
        Page<QuestionBank> page = bankRepository.search(blankToNull(topic), active, pageable);
        Map<Long, String> creators = creatorUuids(page.getContent());
        return PageResponse.from(page.map(b ->
                toBankResponse(b, itemRepository.countByBankId(b.getId()), creators)));
    }

    @Transactional
    public QuestionBankItemResponse addQuestion(Long bankId, AddBankQuestionRequest request, Long callerUserId) {
        QuestionBank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.QUESTION_BANK_NOT_FOUND, bankId));

        QuestionBankItem item = new QuestionBankItem();
        item.setBank(bank);
        item.setQuestionText(request.questionText());
        item.setQuestionType(request.questionType());
        item.setOptions(toJson(request.options()));
        item.setCorrectAnswer(toJson(request.correctAnswerIndices()));
        item.setMarks(request.marks());
        item.setExplanation(blankToNull(request.explanation()));
        item.setDifficulty(request.difficulty());
        item.setCreatedBy(callerUserId);
        itemRepository.save(item);

        return toItemResponse(item, creatorUuids(item));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionBankItemResponse> listQuestions(Long bankId, Pageable pageable) {
        if (!bankRepository.existsById(bankId)) {
            throw new ResourceNotFoundException(ErrorCode.QUESTION_BANK_NOT_FOUND, bankId);
        }
        Page<QuestionBankItem> page = itemRepository.findByBankId(bankId, pageable);
        List<Long> creatorIds = page.getContent().stream().map(QuestionBankItem::getCreatedBy).distinct().toList();
        Map<Long, String> creators = creatorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(creatorIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUuid));
        return PageResponse.from(page.map(i -> toItemResponse(i, creators.get(i.getCreatedBy()))));
    }

    private Map<Long, String> creatorUuids(List<QuestionBank> banks) {
        List<Long> ids = banks.stream().map(QuestionBank::getCreatedBy).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUuid));
    }

    private String creatorUuids(QuestionBankItem item) {
        return userRepository.findById(item.getCreatedBy()).map(User::getUuid).orElse(null);
    }

    private QuestionBankResponse toBankResponse(QuestionBank b, long questionCount, Map<Long, String> creators) {
        return new QuestionBankResponse(
                b.getId(), b.getName(), b.getTopic(), b.getDescription(), b.isActive(),
                questionCount, creators.get(b.getCreatedBy()), b.getCreatedAt(), b.getUpdatedAt());
    }

    private QuestionBankItemResponse toItemResponse(QuestionBankItem i, String createdByUuid) {
        return new QuestionBankItemResponse(
                i.getId(), i.getBank().getId(), i.getQuestionText(), i.getQuestionType(),
                fromJsonStrings(i.getOptions()), i.getMarks(), i.getExplanation(), i.getDifficulty(),
                createdByUuid, i.getCreatedAt());
    }

    private String toJson(List<?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize question bank JSON", e);
        }
    }

    private List<String> fromJsonStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return objectMapper.readValue(json, listType);
        } catch (Exception e) {
            log.warn("[assessments/banks] unparseable options JSON on a bank item, treating as empty");
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
