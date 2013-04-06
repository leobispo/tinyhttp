package br.com.is.http.server.exception;

public class InternalServerErrorException extends HTTPRequestException {

  private static final long serialVersionUID = -6678182696175846949L;

  public InternalServerErrorException(String message) {
    super(500, message);
  }

  public InternalServerErrorException(String message, Throwable cause) {
    super(500, message, cause);
  }
}
