package br.com.is.http.server.exception;

public class RequestEntityTooLargeException extends HTTPRequestException {

  private static final long serialVersionUID = -1865558019389817517L;

  public RequestEntityTooLargeException() {
  }

  public RequestEntityTooLargeException(String message) {
    super(message);
  }

  public RequestEntityTooLargeException(Throwable cause) {
    super(cause);
  }

  public RequestEntityTooLargeException(String message, Throwable cause) {
    super(message, cause);
  }

  public RequestEntityTooLargeException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
