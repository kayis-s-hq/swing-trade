package com.swingtrade.llm.service;

import org.junit.jupiter.api.Test;
import com.openai.errors.BadRequestException;
import com.openai.core.http.Headers;
import com.openai.models.ErrorObject;

import static org.assertj.core.api.Assertions.assertThat;

class LlmErrorUtilsTest {

    @Test
    void describesOrdinaryErrorsUsingTheirMessage() {
        assertThat(LlmErrorUtils.describeError(new IllegalStateException("upstream unavailable")))
                .isEqualTo("upstream unavailable");
    }

    @Test
    void includesStructuredBadRequestDetails() {
        ErrorObject error = ErrorObject.builder()
                .message("context too long")
                .type("invalid_request_error")
                .code("context_length_exceeded")
                .param("messages")
                .build();
        BadRequestException exception = BadRequestException.builder().headers(Headers.builder().build())
                .error(error).build();

        assertThat(LlmErrorUtils.describeError(exception))
                .contains("400", "type=invalid_request_error", "code=context_length_exceeded",
                        "param=messages", "body=");
    }
}
