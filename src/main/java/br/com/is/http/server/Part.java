package br.com.is.http.server;

import java.io.InputStream;
import java.util.Collection;

public class Part {

  public void delete() {
    
  }
  
  public String getContentType() {
    return "";
  }
  
  public String getHeader(final String name) {
    return "";
  }
  
  public Collection<String> getHeaderNames() {
    return null;
  }
  
  public Collection<String> getHeaders(final String name) {
    return null;
  }
  
  public InputStream getInputStream() {
    return null;
  }
  
  public String getName() {
    return "";
  }
  
  public long getSize() {
    return 0;
  }
  
  public void write(final String fileName) {
  }
}
