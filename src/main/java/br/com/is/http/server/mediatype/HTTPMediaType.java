package br.com.is.http.server.mediatype;

import java.util.Hashtable;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;

public interface HTTPMediaType {
  void process(final HTTPContext context, final HTTPRequest request,final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams);
}
