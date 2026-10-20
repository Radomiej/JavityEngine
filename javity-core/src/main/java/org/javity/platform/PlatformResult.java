package org.javity.platform;

/**
 * Outcome of a platform service request. Errors are values so expected service failures stay
 * explicit.
 */
public final class PlatformResult<T> {
  public enum ErrorCode {
    UNAVAILABLE,
    OFFLINE,
    UNSUPPORTED,
    NOT_AUTHENTICATED,
    NOT_FOUND,
    CONFLICT,
    INVALID_ARGUMENT,
    CANCELLED,
    CLOSED,
    REQUEST_FAILED
  }

  private final T value;
  private final ErrorCode errorCode;
  private final String message;

  private PlatformResult(T value, ErrorCode errorCode, String message) {
    this.value = value;
    this.errorCode = errorCode;
    this.message = message;
  }

  public static <T> PlatformResult<T> success(T value) {
    return new PlatformResult<>(value, null, null);
  }

  public static <T> PlatformResult<T> failure(ErrorCode code, String message) {
    if (code == null) throw new IllegalArgumentException("code is required");
    return new PlatformResult<>(null, code, message == null ? "" : message);
  }

  public boolean isSuccess() {
    return errorCode == null;
  }

  public T value() {
    if (!isSuccess()) throw new IllegalStateException("Request failed: " + errorCode);
    return value;
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public String message() {
    return message;
  }
}
