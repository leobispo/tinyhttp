<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <html>
      <head>
        <title>404 Not Found</title>
      </head>
      <body>
        <h1>Not Found</h1>
        <p>The requested URL <xsl:value-of select="uri" /> was not found on this server.</p>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>