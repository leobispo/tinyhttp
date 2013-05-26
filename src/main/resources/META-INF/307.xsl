<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <html>
      <head>
        <title>307 Temporary Redirect</title>
      </head>
      <body>
        <h1>Temporary Redirect</h1>
        <p>The document has moved  <a><xsl:attribute name='href'><xsl:value-of select="uri" /></xsl:attribute>here</a> .</p>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>