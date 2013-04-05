package br.com.is.http.server.mediatype;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;

public class ApplicationXwwwFormURLEncode implements HTTPMediaType {
  private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  
  private static int INTERNAL_SERVER_ERROR = 500;
  
  @Override
  public void process(final HTTPContext context, final HTTPRequest request,final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams) {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
    String line;
    try {
      while ((line = reader.readLine()) != null)
        parseParams(line, requestParams);
      
      context.doPost(request, response);
    }
    catch (IOException e) {
      if (LOGGER.isLoggable(Level.WARNING))
        LOGGER.log(Level.WARNING, "Problems to read data from the HTTP Channel", e);

      response.sendError(INTERNAL_SERVER_ERROR);
    }
  }
    
  /**
   * Parse the get/post params and add it to the params map.
   * 
   * @param src Data to be parsed.
   * 
   */
  private void parseParams(final String src, final Hashtable<String, String> params) {
    final StringTokenizer st = new StringTokenizer(src, "&");
    if (st.hasMoreTokens()) {
      do {
        final String tmp = st.nextToken();
        int idx = tmp.indexOf('=');
        if (idx >= 0)
          params.put(decodeHEXUri(tmp.substring(0, idx)).trim(), decodeHEXUri(tmp.substring(idx +1)));
      } while (st.hasMoreTokens());
    }
  }

  /**
   * Decode the HEX URI data.
   * 
   * @param src Data to be decoded.
   * 
   * @return Decoded URI.
   * 
   */
  private String decodeHEXUri(final String src) {
    final StringBuilder sb = new StringBuilder();

    for (int i = 0; i < src.length(); ++i) {
      char ch = src.charAt(i);
      switch (ch) {
      case '+':
        sb.append(' ');
        break;
      case '%':
        sb.append((char) Integer.parseInt(src.substring(i + 1, i + 3), 16));
        i += 2;
        break;
      default:
        sb.append(ch);
      }
    }

    return sb.toString();
  }
}
