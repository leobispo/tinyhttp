package br.com.is.http.server;

public class Cookie {
  private final String name;
  private final String value;
  
  public Cookie(final String name, final String value) {
    this.name  = name;
    this.value = value;
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
    return name;
  }
  
  public String getPath() {
    return "";
  }
  
  public boolean getSecure() {
    return false;
  }

  public String getValue() {
    return value;
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

  public void setVersion(int v) {

  }
}
