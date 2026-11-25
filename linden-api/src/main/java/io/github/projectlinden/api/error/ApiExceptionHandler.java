package io.github.projectlinden.api.error;

import io.github.projectlinden.api.logging.CorrelationIdFilter;
import io.github.projectlinden.core.circle.NotAMemberException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.*;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The one place where exceptions become HTTP responses (ENGINEERING.md § 7).
 *
 * <p>Every error is a Problem Details response (RFC 9457) with a stable {@code code} and the request's
 * {@code correlationId}. Clients never see internals – no stack traces, SQL or exception messages. The cause of an
 * unexpected error is logged, in a line that carries the same correlation ID.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Errors Spring already knows (404, 405, 400, …): add the stable code and the correlation ID.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
                                                                       HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            problem.setDetail(detailFor(statusCode));
            problem.setProperty("code", codeFor(statusCode));
            addCorrelationId(problem);
        }
        return response;
    }

    /**
     * Invalid request body (Bean Validation): 400 with the field and the reason – never the rejected value, so content
     * is never echoed back (ENGINEERING.md § 7).
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = handleExceptionInternal(exception, null, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                    .map(error -> Map.of(
                            "field", error.getField(),
                            "reason", Objects.requireNonNullElse(error.getDefaultMessage(), "invalid")))
                    .toList();
            problem.setProperty("errors", errors);
        }
        return response;
    }

    /**
     * Not a member of the circle – or it does not exist: 404 for both, so nobody learns which circles exist by trying
     * ids. An expected case, not an error: logged at DEBUG only.
     */
    @ExceptionHandler(NotAMemberException.class)
    ProblemDetail handleNotAMember(NotAMemberException exception) {
        LOGGER.debug("Not a member of circle {} (or it does not exist)", exception.getCircleId());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "This circle doesn't exist, or you're not a member of it.");
        problem.setTitle("Circle not found");
        problem.setProperty("code", "circle_not_found");
        addCorrelationId(problem);
        return problem;
    }

    /**
     * The database can't be reached right now – stopped, connection lost, or too slow: 503, so the app can say
     * "try again in a moment". Every other database error is a bug and stays a 500 ({@link #handleUnexpected}).
     */
    @ExceptionHandler({
            CannotCreateTransactionException.class,      // no connection when a transaction starts (a stopped database)
            DataAccessResourceFailureException.class,    // the connection broke during the work
            TransientDataAccessResourceException.class,  // the database was briefly unavailable
            QueryTimeoutException.class                  // the database didn't answer in time
    })
    ProblemDetail handleDatabaseUnavailable(Exception exception) {
        LOGGER.warn("Database unavailable while handling a request", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "We can't reach our storage right now. Please try again in a moment.");
        problem.setTitle("Service unavailable");
        problem.setProperty("code", "service_unavailable");
        addCorrelationId(problem);
        return problem;
    }

    /**
     * Everything unexpected: 500, the cause only in the log, a calm message for the client.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOGGER.error("Unexpected error while handling a request", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again later.");
        problem.setTitle("Internal error");
        problem.setProperty("code", "internal_error");
        addCorrelationId(problem);
        return problem;
    }

    /**
     * A stable, machine-readable code derived from the status, e.g. 404 → "not_found".
     */
    private static String codeFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? "error_" + statusCode.value() : status.name().toLowerCase(Locale.ROOT);
    }

    /**
     * A calm, generic text – Spring's own details can reveal internals (e.g. "No static resource …").
     */
    private static String detailFor(HttpStatusCode statusCode) {
        return statusCode.is4xxClientError()
                ? "The request could not be handled. Check the address and the data you sent."
                : "Something went wrong on our side. Please try again later.";
    }

    private static void addCorrelationId(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
