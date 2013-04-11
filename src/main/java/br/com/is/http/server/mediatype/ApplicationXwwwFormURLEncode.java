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
package br.com.is.http.server.mediatype;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.StringTokenizer;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;
import br.com.is.http.server.Part;
import br.com.is.http.server.exception.HTTPRequestException;
import br.com.is.http.server.exception.InternalServerErrorException;

public class ApplicationXwwwFormURLEncode implements HTTPMediaType {
  @Override
  public void process(final HTTPContext context, final HTTPRequest request,final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams,
    final Hashtable<String, Part> parts) throws HTTPRequestException {
    final BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
    String line;
    try {
      while ((line = reader.readLine()) != null)
        parseParams(line, requestParams);
      
      context.doPost(request, response);
    }
    catch (IOException e) {
      throw new InternalServerErrorException("Problems to read data from the HTTP Channel", e);
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
