package com.api.v1.newssystem.model.response;

import com.api.v1.newssystem.model.enumerate.NewsStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsDtoJsonTest {

    @Test
    void dates_areWrittenAsReadableIsoText_andTheRejectionReasonIsIncluded() {
        NewsDto dto = new NewsDto(1L, "Title", "title", null, "Body", null, NewsStatus.REJECTED, 7L, "Author Name", null, List.of(), List.of(),
                true, "Please add sources", true, 0, null, LocalDateTime.of(2026, 10, 17, 12, 30), LocalDateTime.of(2026, 10, 18, 8, 5, 9));

        String json = JsonMapper.builder().build().writeValueAsString(dto);

        assertThat(json).contains("\"createdAt\":\"2026-10-17T12:30:00\"");
        assertThat(json).contains("\"updatedAt\":\"2026-10-18T08:05:09\"");
        assertThat(json).contains("\"rejectionReason\":\"Please add sources\"");
        assertThat(json).contains("\"status\":\"REJECTED\"");
        assertThat(json).contains("\"editable\":true");
        assertThat(json).contains("\"authorId\":7");
        assertThat(json).contains("\"authorName\":\"Author Name\"");
    }
}
