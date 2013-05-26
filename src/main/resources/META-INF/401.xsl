<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <html>
      <head>
        <title>401 Authorization Required</title>
      </head>
      <body>
        <h1>Authorization Required</h1>
        <p>This server could not verify that you
           are authorized to access the document
           requested.  Either you supplied the wrong
           credentials (e.g., bad password), or your
           browser doesn't understand how to supply
           the credentials required.</p>
        <p>Additionally, a 404 Not Found error was encountered while trying to use an ErrorDocument to handle the request.</p>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>