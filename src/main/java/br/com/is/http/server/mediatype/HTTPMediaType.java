package br.com.is.http.server.mediatype;

import java.util.Hashtable;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;
import br.com.is.http.server.Part;
import br.com.is.http.server.exception.HTTPRequestException;

public interface HTTPMediaType {
  void process(final HTTPContext context, final HTTPRequest request,final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams, 
    final Hashtable<String, Part> parts) throws HTTPRequestException;
}
