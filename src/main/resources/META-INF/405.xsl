<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <html>
      <head>
        <title>405 Method Not Allowed</title>
      </head>
      <body>
        <h1>Method Not Allowed</h1>
        <p>The requested method GET is not allowed for the URL <xsl:value-of select="uri" />.</p>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>