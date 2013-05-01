/* Copyright (C) 2013 Leonardo Bispo de Oliveira
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package br.com.is.http.server;

import java.util.HashMap;
import java.util.Map;

/**
 * All possible HTTP Status.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
public enum HTTPStatus {  
  CONTINUE(100) {
    public String toString() {
      return "Continue";
    }
  },
  SWITCHING_PROTOCOLS(101) {
    public String toString() {
      return "Switching Protocols";
    }
  },
  OK(200) {
    public String toString() {
      return "OK";
    }
  },
  CREATED(201) {
    public String toString() {
      return "Created";
    }
  },
  ACCEPTED(202) {
    public String toString() {
      return "Accepted";
    }
  },
  NON_AUTHORITATIVE_INFORMATION(203) {
    public String toString() {
      return "Non-Authoritative Information";
    }
  },
  NO_CONTENT(204) {
    public String toString() {
      return "No Content";
    }
  },
  RESET_CONTENT(205) {
    public String toString() {
      return "Reset Content";
    }
  },
  PARTIAL_CONTENT(206) {
    public String toString() {
      return "Multiple Choices";
    }
  },
  MULTIPLE_CHOICES(300) {
    public String toString() {
      return "Multiple Choices";
    }
  },
  MOVED_PERMANENTLY(301) {
    public String toString() {
      return "Moved Permanently";
    }
  },
  FOUND (302) {
    public String toString() {
      return "Found";
    }
  },
  SEE_OTHER(303) {
    public String toString() {
      return "See Other";
    }
  },
  NOT_MODIFIED(304) {
    public String toString() {
      return "Not Modified";
    }
  },
  USE_PROXY(305) {
    public String toString() {
      return "Use Proxy";
    }
  },
  TEMPORARY_REDIRECT(307) {
    public String toString() {
      return "Temporary Redirect";
    }
  },
  BAD_REQUEST(400) {
    public String toString() {
      return "Bad Request";
    }
  },
  UNAUTHORIZED(401) {
    public String toString() {
      return "Unauthorized";
    }
  },
  PAYMENT_REQUIRED(402) {
    public String toString() {
      return "Payment Required";
    }
  },
  FORBIDDEN(403) {
    public String toString() {
      return "Forbidden";
    }
  },
  NOT_FOUND(404) {
    public String toString() {
      return "Not Found";
    }
  },
  METHOD_NOT_ALLOWED(405) {
    public String toString() {
      return "Method Not Allowed";
    }
  },
  NOT_ACCEPTABLE(406) {
    public String toString() {
      return "Not Acceptable";
    }
  },
  PROXY_AUTHENTICATION_REQUIRED(407) {
    public String toString() {
      return "Proxy Authentication Required";
    }
  },
  REQUEST_TIMEOUT(408) {
    public String toString() {
      return "Request Time-out";
    }
  },
  CONFLICT(409) {
    public String toString() {
      return "Conflict";
    }
  },
  GONE(410) {
    public String toString() {
      return "Gone";
    }
  },
  LENGTH_REQUIRED(411) {
    public String toString() {
      return "Length Required";
    }
  },
  PRECONDITION_FAILED(412) {
    public String toString() {
      return "Precondition Failed";
    }
  },
  REQUEST_ENTITY_TOO_LARGE(413) {
    public String toString() {
      return "Request Entity Too Large";
    }
  },
  REQUEST_URI_TOO_LARGE(414) {
    public String toString() {
      return "Request-URI Too Large";
    }
  },
  UNSUPORTED_MEDIA_TYPE(415) {
    public String toString() {
      return "Unsupported Media Type";
    }
  },
  REQUEST_RANGE_NOT_SATISFIABLE(416) {
    public String toString() {
      return "Requested range not satisfiable";
    }
  },
  INTERNAL_SERVER_ERROR(500) {
    public String toString() {
      return "Internal Server Error";
    }
  },
  NOT_IMPLEMENTED(501) {
    public String toString() {
      return "Not Implemented";
    }
  },
  BAD_GATEWAY(502) {
    public String toString() {
      return "Bad Gateway";
    }
  },
  SERVICE_UNAVAILABLE(503) {
    public String toString() {
      return "Service Unavailable";
    }
  },
  GATEWAY_TIMEOUT(504) {
    public String toString() {
      return "Gateway Time-out";
    }
  },
  HTTP_VERSION_NOT_SUPPORTED(505) {
    public String toString() {
      return "HTTP Version not supported";
    }
  };
  
  public int getValue() {
    return id;
  }
 
  private final int id;
  private HTTPStatus(int id) {
    this.id = id;
  }
  
  private static final Map<Integer, HTTPStatus> map = new HashMap<>();
  static {
    for (HTTPStatus type : HTTPStatus.values()) {
      map.put(type.getValue(), type);
    }
  }

  public static HTTPStatus fromInt(int id) {
    HTTPStatus type = map.get(id);
    if (type == null)
      return HTTPStatus.BAD_REQUEST;

    return type;
  }
}
