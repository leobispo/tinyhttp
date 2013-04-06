package br.com.is.http.server.exception;

public class HTTPRequestException extends Exception {
  private static final long serialVersionUID = -4075767828421001782L;

  private final int error;
  public HTTPRequestException(final int error, final String message) {
    super(message);
    this.error = error;
  }

  public HTTPRequestException(final int error, final String message, final Throwable cause) {
    super(message, cause);
    this.error = error;
  }

  public int getError() {
    return error;
  }
}
