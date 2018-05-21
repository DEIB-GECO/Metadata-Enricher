import java.io.File

import DBcon.{db_handler, query_handler}
import play.api.libs.json.Json
import scalaj.http.{Http, HttpOptions}
import com.github.tototoshi.csv._

object score_calculator {

  val apikey = "2338fb64-0246-4627-bf4d-4197bc8c9c64"
  val url = "http://data.bioontology.org/recommender"

  def get_recommender_score (term: String, onto: String): Double = {
    var score = 0.0

    val params = Seq("apikey" -> apikey, "input" -> term, "input_type" -> "2", "output_type" -> "1", "display_context"->"false","display_links"->"false","ontologies"->onto.map(_.toUpper),
      "wc"->"0","wa"->"1","wd"->"0","ws"->"0")
    val response = Http(url).params(params).header("accept", "text/json").option(HttpOptions.connTimeout(10000)).option(HttpOptions.readTimeout(50000)).asString.body
    val j = Json.parse(response) \\ "evaluationScore"
    if(j.nonEmpty)
      score = j.head.validate[Double].get
    score
  }

  def get_match_score (id: Int, service: String): Int = {
    var score = 0
    val matchType = db_handler.get_match_type(id, service)

    if (service.equalsIgnoreCase("zooma")) {
      if (matchType.equalsIgnoreCase("HIGH")) score = 10
      else if (matchType.equalsIgnoreCase("GOOD")) score = 7
      else if (matchType.equalsIgnoreCase("MEDIUM")) score = 5
      else if (matchType.equalsIgnoreCase("LOW")) score = 3
    }
    else {
      if (matchType.startsWith("PREF")) {
        if (matchType.contains("-")) {
          val lscore = matchType.split("-")
          println("dentro ols  "+matchType)
          score = 10 - lscore(1).drop(1).toInt
        }
        else score = 10
      }
      else if (matchType.startsWith("SYN")) {
        if (matchType.contains("-")) {
          val lscore = matchType.split("-")
          println("dentro ols  "+matchType)
          score = 5 - lscore(1).drop(1).toInt
        }
        else score = 5
      }
      else score = 1
    }
    if(score<0)
      score = 0

    score
  }

  def calculate_ontology_score(t:String): Unit = {
    var ontologies: Seq[String] = List()
    var insert: Seq[(String,String,String)] = List()
    var score: Seq[Double] = List()
    val onto_recsys = db_handler.get_ontology_by_type(t)

    println("inizio")
    main.get_timestamp()
    for (onto <- onto_recsys) {
      val term = db_handler.get_parsed_by_ontology(onto, t)
      var recsys_score = 0.0

      recsys_score = get_recommender_score(term,onto)

      insert :+= (onto,t,recsys_score.toString)
    }
    println("ok "+t)
    main.get_timestamp()
    db_handler.ontology_score_insert(insert)
  }

  def calculate_score(): Unit = {
    val range = db_handler.get_db_lenght()
    var result: Seq[List[String]] = List()
    val f = new File("out2.csv")
    main.get_timestamp()
    for (i <- 1 to range){
      val a = db_handler.get_onto_service_termtype(i)
      val onto = a._1
      val tt = a._3
      val onto_score = db_handler.get_onto_score(onto,tt)
      val match_score = get_match_score(i, a._2)
      val suitability = calculate_suitability_score(tt, onto)

      var score = onto_score.toDouble * match_score.doubleValue

      if (score<0)
        score=0

      db_handler.update_score(score,match_score,onto_score.toDouble,suitability,i)
    }
    main.get_timestamp()

  }


  def calculate_suitability_score(t: String, o: String): Double = {
    val ontos = db_handler.get_ontology_by_type(t)
    var result: Seq[List[String]] = List()

    var suitability = 0.0
    val tmp_coverage = db_handler.get_onto_coverage(o,"tissue")
    val coverage = tmp_coverage._1.toDouble
    val no_annotations = tmp_coverage._2.toInt
    val matchscore = db_handler.get_onto_matchscore(o,"tissue").toInt
    suitability = (matchscore/no_annotations) * coverage

    suitability
  }
}