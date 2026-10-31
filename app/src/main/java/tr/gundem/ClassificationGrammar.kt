package tr.gundem

/** Seven independent topic decisions avoid ordered-list sampling bias. */
fun classificationGrammar():String="""
root ::= "[" "[" bit "," bit "," bit "," bit "," bit "," bit "," bit "]" "," kind "," score "," score "," score "]"
bit ::= [01]
kind ::= "\"ACIKLAMA\"" | "\"CAGRI\"" | "\"SOMUT_GELISME\"" | "\"YORUM\""
score ::= [0-3]
""".trimIndent()
