package com.swingtrade.llm.service;

/**
 * openai-java's {@link com.openai.errors.BadRequestException#getMessage()} is
 * just "400: Unknown" — the SDK doesn't surface the actual server-provided
 * error body in the message, only in structured accessors. Without this, every
 * llama.cpp rejection (bad request, context length, unsupported field, etc.)
 * logs and persists as the same useless string, making root-causing impossible
 * from logs/audit alone. Shared by SentimentService and SynthesisService since
 * both call llama.cpp through the same openai-java-backed client.
 */
final class LlmErrorUtils {

    private LlmErrorUtils() {
    }

    static String describeError(Throwable e) {
        if (e instanceof com.openai.errors.BadRequestException bre) {
            StringBuilder sb = new StringBuilder("400");
            bre.type().ifPresent(t -> sb.append(" type=").append(t));
            bre.code().ifPresent(c -> sb.append(" code=").append(c));
            bre.param().ifPresent(p -> sb.append(" param=").append(p));
            sb.append(" body=").append(bre.body());
            return sb.toString();
        }
        return e.getMessage();
    }
}
