package recommender.ontologies.Parsers

import play.api.libs.json._
import scalaj.http.{Http, HttpOptions}
import utilities.Preprocessing.lookup

object ZoomaParser {

  def parse (string: String, termAnnotated: String): List[List[String]] = {
    var rows: Seq[List[String]] = List()
    val service = "ZOOMA"
    val raw_value = lookup(termAnnotated)
    val parsed_value = termAnnotated
    val j = Json.parse(string)
    val range = (j \\ "confidence").indices

    for (i <- range) {
      val j2 = j(i)
      val url_l = (j2 \ "_links" \ "olslinks").get
      val url_range = (url_l \\ "href").indices
      for (m <- url_range) {
        val url = (url_l(m) \ "href").validate[String].get
        val j3 = (Json.parse(Http(url).param("rows","15").option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString.body) \ "_embedded" \ "terms").getOrElse(null)
        if (j3 != null) {
          val range2 = (j3 \\ "synonyms").indices
          val score = (j2 \ "confidence").validate[String].get
          for (k <- range2) {
            val j4 = j3(k)
            val prefLabel = (j4 \ "iri").validate[String].get
            val ontology = (j4 \ "ontology_name").validate[String].get//.split("_").head
            val ontology_id = (j4 \ "short_form").validate[String].get
            val id = ontology_id.substring(ontology_id.lastIndexOf("_") + 1)
            val synonym = (j4 \ "synonyms").validate[List[String]].getOrElse(List()).mkString(",")
            val term_type = ""
            val current = List(service,raw_value,parsed_value,ontology.map(_.toLower) ,ontology_id,prefLabel,synonym,score,term_type)
            if (!rows.exists(p => p.equals(current)))
              rows :+= current
          }
        }
      }
    }
    rows.toList.distinct
    //END PARSE
  }
}
