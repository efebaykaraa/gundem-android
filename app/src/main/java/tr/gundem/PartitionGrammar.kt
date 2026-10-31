package tr.gundem

/** Canonical partitions, each index exactly once. Subset rules avoid Bell-number expansion. */
fun partitionGrammar(size:Int):String {
    require(size in 2..6)
    val full=(1 shl size)-1
    return buildString {
        append("root ::= \"[\" ws p$full ws \"]\" ws\n")
        append("ws ::= [ \\t\\n\\r]?\n")
        for(mask in 1..full) {
            val indices=(1..size).filter { mask and (1 shl (it-1))!=0 }
            append("g$mask ::= \"[\" ws ")
            append(indices.joinToString(" ws \",\" ws ") { "\"$it\"" });append(" ws \"]\"\n")
            val first=Integer.lowestOneBit(mask)
            val options=(1..mask).filter { it and mask==it && it and first!=0 }.map { sub ->
                val rest=mask xor sub
                if(rest==0) "g$sub" else "g$sub ws \",\" ws p$rest"
            }
            append("p$mask ::= "+options.joinToString(" | ")+"\n")
        }
    }
}
