package br.com.is.http.server.exception;

import br.com.is.http.server.HTTPStatus;

public class RequestEntityTooLargeException extends HTTPRequestException {

  private static final long serialVersionUID = -7904180576452823797L;

  public RequestEntityTooLargeException(String message) {
    super(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, message);
  }

  public RequestEntityTooLargeException(String message, Throwable cause) {
    super(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, message, cause);
  }
}
