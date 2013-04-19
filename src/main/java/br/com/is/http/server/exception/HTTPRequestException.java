package br.com.is.http.server.exception;

import br.com.is.http.server.HTTPStatus;

public class HTTPRequestException extends Exception {
  private static final long serialVersionUID = -4075767828421001782L;

  private final HTTPStatus error;
  public HTTPRequestException(final HTTPStatus error, final String message) {
    super(message);
    this.error = error;
  }

  public HTTPRequestException(final HTTPStatus error, final String message, final Throwable cause) {
    super(message, cause);
    this.error = error;
  }

  public HTTPStatus getError() {
    return error;
  }
}
