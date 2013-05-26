<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <html>
      <head>
        <title>403 Forbidden</title>
      </head>
      <body>
        <h1>Forbidden</h1>
        <p>You don't have permission to access <xsl:value-of select="uri" /> on this server.</p>
        <p>Additionally, a 404 Not Found error was encountered while trying to use an ErrorDocument to handle the request.</p>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>