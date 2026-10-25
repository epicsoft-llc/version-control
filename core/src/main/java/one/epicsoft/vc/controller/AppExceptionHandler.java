package one.epicsoft.vc.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.annotation.Nonnull;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.dto.ErrorDto;
import one.epicsoft.vc.exception.AppConflictException;
import one.epicsoft.vc.exception.AppException;
import one.epicsoft.vc.exception.AppNotFoundException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static java.util.Objects.requireNonNullElse;

/**
 * Turns every exception into the same {@link ErrorDto}.
 *
 * <p>The response carries a translated sentence, the log carries the technical
 * message. Stack traces and class names never reach the client.
 *
 * <p>This advice wins over Spring's own handling for framework exceptions too, so
 * every client error has to be caught here by name. Whatever is not listed lands
 * on the catch-all and is reported as a server error — which is right for a real
 * defect and wrong for a mistyped id.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class AppExceptionHandler {

  private static final String KEY_VALIDATION = "error.validation";
  private static final String KEY_UNEXPECTED = "error.unexpected";
  private static final String KEY_NOT_FOUND = "error.notFound";
  private static final String KEY_METHOD = "error.methodNotAllowed";
  private static final String KEY_BAD_REQUEST = "error.badRequest";

  private final MessageSource messageSource;

  @ExceptionHandler(AppNotFoundException.class)
  public ResponseEntity<ErrorDto> handleNotFound(@Nonnull final AppNotFoundException exception) {
    log.debug("Not found: {}", exception.getMessage());
    return this.build(HttpStatus.NOT_FOUND, exception.getMessageKey(), List.of());
  }

  @ExceptionHandler(AppConflictException.class)
  public ResponseEntity<ErrorDto> handleConflict(@Nonnull final AppConflictException exception) {
    log.debug("Conflict: {}", exception.getMessage());
    return this.build(HttpStatus.CONFLICT, exception.getMessageKey(), List.of());
  }

  @ExceptionHandler(AppException.class)
  public ResponseEntity<ErrorDto> handleApp(@Nonnull final AppException exception) {
    log.warn("Domain error: {}", exception.getMessage());
    return this.build(HttpStatus.BAD_REQUEST, exception.getMessageKey(), List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDto> handleValidation(@Nonnull final MethodArgumentNotValidException exception) {
    final List<ErrorDto.FieldErrorDto> fields = exception.getBindingResult()
                                                         .getFieldErrors()
                                                         .stream()
                                                         .map(error -> new ErrorDto.FieldErrorDto(error.getField(), error.getDefaultMessage()))
                                                         .toList();
    log.debug("Validation failed: {}", fields);
    return this.build(HttpStatus.BAD_REQUEST, KEY_VALIDATION, fields);
  }

  /** The same answer for a body that is validated after binding — a merged patch, for one. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorDto> handleConstraintViolation(@Nonnull final ConstraintViolationException exception) {
    final List<ErrorDto.FieldErrorDto> fields = exception.getConstraintViolations()
                                                         .stream()
                                                         .map(violation -> new ErrorDto.FieldErrorDto(violation.getPropertyPath().toString(),
                                                                                                      violation.getMessage()))
                                                         .sorted(Comparator.comparing(ErrorDto.FieldErrorDto::getField))
                                                         .toList();
    log.debug("Validation failed: {}", fields);
    return this.build(HttpStatus.BAD_REQUEST, KEY_VALIDATION, fields);
  }

  /**
   * A missing parameter, an id that is not a uuid, a body that is not JSON. All of
   * them are the caller's mistake and must not be logged as a server error.
   */
  @ExceptionHandler({MissingServletRequestParameterException.class,
                     MethodArgumentTypeMismatchException.class,
                     HttpMessageNotReadableException.class,
                     HandlerMethodValidationException.class})
  public ResponseEntity<ErrorDto> handleBadRequest(@Nonnull final Exception exception) {
    log.debug("Bad request: {}", exception.getMessage());
    return this.build(HttpStatus.BAD_REQUEST, KEY_BAD_REQUEST, List.of());
  }

  /**
   * A path nobody serves. Without this the catch-all below would answer 500 and
   * log a stack trace for every stray request.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorDto> handleNoResource(@Nonnull final NoResourceFoundException exception) {
    log.debug("No handler for '{}'", exception.getResourcePath());
    return this.build(HttpStatus.NOT_FOUND, KEY_NOT_FOUND, List.of());
  }

  /** The status says which half of the content negotiation failed. */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorDto> handleUnsupportedMediaType(@Nonnull final HttpMediaTypeNotSupportedException exception) {
    log.debug("Unsupported media type: {}", exception.getMessage());
    return this.build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, KEY_BAD_REQUEST, List.of());
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<ErrorDto> handleNotAcceptable(@Nonnull final HttpMediaTypeNotAcceptableException exception) {
    log.debug("Not acceptable: {}", exception.getMessage());
    return this.build(HttpStatus.NOT_ACCEPTABLE, KEY_BAD_REQUEST, List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorDto> handleMethod(@Nonnull final HttpRequestMethodNotSupportedException exception) {
    log.debug("Method not allowed: {}", exception.getMessage());
    return this.build(HttpStatus.METHOD_NOT_ALLOWED, KEY_METHOD, List.of());
  }

  // Declared once here, the API description lists this shape as the error answer of every operation.
  @ApiResponse(responseCode = "default",
               description = "Any error: 400 malformed or against a rule, 404 unknown id, 409 name taken, 500 a defect.",
               content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDto.class)))
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorDto> handleUnexpected(@Nonnull final Exception exception) {
    log.error("Unexpected error", exception);
    return this.build(HttpStatus.INTERNAL_SERVER_ERROR, KEY_UNEXPECTED, List.of());
  }

  @Nonnull
  private ResponseEntity<ErrorDto> build(@Nonnull final HttpStatus status,
                                         @Nonnull final String messageKey,
                                         @Nonnull final List<ErrorDto.FieldErrorDto> fields) {
    final String message = this.messageSource.getMessage(messageKey, null, null, LocaleContextHolder.getLocale());
    return ResponseEntity.status(status)
                         .body(new ErrorDto(OffsetDateTime.now(),
                                            status.value(),
                                            requireNonNullElse(message, messageKey),
                                            fields));
  }
}
