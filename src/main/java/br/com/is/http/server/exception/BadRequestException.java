package br.com.is.http.server.exception;

public class BadRequestException extends HTTPRequestException {

  private static final long serialVersionUID = 6614991236571962812L;

  public BadRequestException(String message) {
    super(400, message);
  }

  public BadRequestException(String message, Throwable cause) {
    super(400, message, cause);
  }
}
