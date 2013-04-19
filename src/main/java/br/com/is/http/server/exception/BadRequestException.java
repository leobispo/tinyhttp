package br.com.is.http.server.exception;

import br.com.is.http.server.HTTPStatus;

public class BadRequestException extends HTTPRequestException {

  private static final long serialVersionUID = 6614991236571962812L;

  public BadRequestException(String message) {
    super(HTTPStatus.BAD_REQUEST, message);
  }

  public BadRequestException(String message, Throwable cause) {
    super(HTTPStatus.BAD_REQUEST, message, cause);
  }
}
