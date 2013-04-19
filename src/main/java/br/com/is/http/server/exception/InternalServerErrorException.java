package br.com.is.http.server.exception;

import br.com.is.http.server.HTTPStatus;

public class InternalServerErrorException extends HTTPRequestException {

  private static final long serialVersionUID = -6678182696175846949L;

  public InternalServerErrorException(String message) {
    super(HTTPStatus.INTERNAL_SERVER_ERROR, message);
  }

  public InternalServerErrorException(String message, Throwable cause) {
    super(HTTPStatus.INTERNAL_SERVER_ERROR, message, cause);
  }
}
