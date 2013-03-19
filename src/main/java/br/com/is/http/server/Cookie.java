package br.com.is.http.server;

public class Cookie {
  public Object clone() {
    return null;
  }

  public String getComment() {
    return "";
  }

  public String getDomain() {
    return "";
  }

  public int getMaxAge() {
    return 0;
  }

  public String getName() {
    return "";
  }
  
  public String getPath() {
    return "";
  }
  
  public boolean getSecure() {
    return false;
  }

  public String getValue() {
    return "";
  }
  
  public int getVersion() {
    return 0;
  }
  
  public boolean isHttpOnly() {
    return false;
  }

  public void setComment(final String purpose) {

  }
  
  public void setDomain(final String domain) {

  }
  
  public void setHttpOnly(boolean isHttpOnly) {

  }
  
  public void setMaxAge(int expiry) {

  }
  
  public void setPath(final String uri) {

  }
  
  public void setSecure(boolean flag) {

  }

  public void setValue(final String newValue) {

  }

  public void setVersion(int v) {

  }
}
