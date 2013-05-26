<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <html>
      <head>
        <title>413 Request Entity Too Large</title>
      </head>
      <body>
        <h1>Request Entity Too Large</h1>
        The requested resource<br /><xsl:value-of select="uri" /><br />does not allow request data with GET requests, or the amount of data provided in the request exceeds the capacity limit.
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>