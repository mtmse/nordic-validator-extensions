#!/bin/bash

SCHXSLT_PATH=schxslt

mkdir -p src/main/resources/dtbook/compiled
saxon11-xslt -s:src/main/resources/dtbook/sch/dtbook.mathml.nimas.sch -o:src/main/resources/dtbook/compiled/dtbook.mathml.nimas.xsl -xsl:$SCHXSLT_PATH/src/main/resources/content/transpile.xsl
saxon11-xslt -s:src/main/resources/dtbook/sch/dtbook.mathml.sch -o:src/main/resources/dtbook/compiled/dtbook.mathml.xsl -xsl:$SCHXSLT_PATH/src/main/resources/content/transpile.xsl

mkdir -p src/main/resources/daisy3/compiled
saxon11-xslt -s:src/main/resources/daisy3/sch/daisy3.opf.sch -o:src/main/resources/daisy3/compiled/daisy3.opf.xsl -xsl:$SCHXSLT_PATH/src/main/resources/content/transpile.xsl
