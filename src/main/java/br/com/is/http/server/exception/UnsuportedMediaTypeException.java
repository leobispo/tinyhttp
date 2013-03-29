package br.com.is.http.server.exception;

public class UnsuportedMediaTypeException extends HTTPRequestException {

  private static final long serialVersionUID = 3359675709702819881L;

  public UnsuportedMediaTypeException() {
  }

  public UnsuportedMediaTypeException(String message) {
    super(message);
  }

  public UnsuportedMediaTypeException(Throwable cause) {
    super(cause);
  }

  public UnsuportedMediaTypeException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnsuportedMediaTypeException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
