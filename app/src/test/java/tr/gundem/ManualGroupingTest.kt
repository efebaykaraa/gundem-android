package tr.gundem

import org.junit.Assert.*
import org.junit.Test

class ManualGroupingTest {
    private fun article(id:String)=Article(id,"source-$id",id,"https://example.org/$id","Aynı başlık","Metin",null,null,id)

    @Test fun identicalHeadlinesAndOldAutomaticVotesDoNotMergeCards() {
        val articles=listOf(article("a"),article("b"),article("c"))
        val old=listOf(StoredPair("a|b","a","b",true,false,"old"))
        assertEquals(setOf(setOf("a"),setOf("b"),setOf("c")),articleGroups(articles,old).map { group->group.map { it.id }.toSet() }.toSet())
    }

    @Test fun explicitMergeAndSplitArePreserved() {
        val articles=listOf(article("a"),article("b"),article("c"))
        val votes=listOf(StoredPair("a|b","a","b",true,true,"user"),StoredPair("b|c","b","c",true,false,"old"))
        assertEquals(setOf(setOf("a","b"),setOf("c")),articleGroups(articles,votes).map { group->group.map { it.id }.toSet() }.toSet())
        val split=listOf(StoredPair("a|b","a","b",false,true,"user"))
        assertEquals(3,articleGroups(articles,split).size)
    }
}
