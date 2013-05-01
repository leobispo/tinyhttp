package br.com.is.http.server.exception;

import br.com.is.http.server.HTTPStatus;

public class RequestRangeNotSatisfiableException extends HTTPRequestException {

  private static final long serialVersionUID = -1755248718977320962L;

  public RequestRangeNotSatisfiableException(String message) {
    super(HTTPStatus.REQUEST_RANGE_NOT_SATISFIABLE, message);
  }

  public RequestRangeNotSatisfiableException(String message, Throwable cause) {
    super(HTTPStatus.REQUEST_RANGE_NOT_SATISFIABLE, message, cause);
  }
}
