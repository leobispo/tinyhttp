package br.com.is.http.server.mediatype;

import java.util.Hashtable;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;

public class MultipartFormData implements HTTPMediaType {
  @Override
  public void process(final HTTPContext context, final HTTPRequest request,final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams) {
    // TODO Auto-generated method stub
  }

  //TODO: Implement another method passing the parts!!
}
