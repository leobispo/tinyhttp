<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/">
    <html>
      <body>
        <xsl:for-each select="files/fileinfo">
          <xsl:choose>
            <xsl:when test="isDir = 'true'">
              <b><a><xsl:attribute name='href'><xsl:value-of select="uri" /></xsl:attribute><xsl:value-of select="file" />/</a></b>
            </xsl:when>
            <xsl:otherwise>
              <a><xsl:attribute name='href'><xsl:value-of select="uri" /></xsl:attribute><xsl:value-of select="file" /></a>&#xa0;
              <font size="2">(
              <xsl:variable name="size" select="length" />
              <xsl:choose>
                <xsl:when test="$size &gt;= 1073741824">
                  <xsl:value-of select="format-number($size div 1073741824,'#,###')"/><xsl:text> GB</xsl:text>
                </xsl:when>
                <xsl:when test="$size &gt;= 1048576">
                  <xsl:value-of select="format-number($size div 1048576,'#,###')"/><xsl:text> MB</xsl:text>
                </xsl:when>
                <xsl:when test="$size &gt;= 1024">
                  <xsl:value-of select="format-number($size div 1024,'#,###')"/><xsl:text> KB</xsl:text>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="$size"/><xsl:text> Bytes</xsl:text>
                </xsl:otherwise>
              </xsl:choose>)</font>
            </xsl:otherwise>
          </xsl:choose>
          <br />
        </xsl:for-each>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>