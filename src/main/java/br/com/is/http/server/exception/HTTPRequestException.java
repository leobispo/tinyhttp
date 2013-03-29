package br.com.is.http.server.exception;

public class HTTPRequestException extends Exception {

  private static final long serialVersionUID = 2130854288812046983L;

  public HTTPRequestException() {
  }

  public HTTPRequestException(String message) {
    super(message);
  }

  public HTTPRequestException(Throwable cause) {
    super(cause);
  }

  public HTTPRequestException(String message, Throwable cause) {
    super(message, cause);
  }

  public HTTPRequestException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
