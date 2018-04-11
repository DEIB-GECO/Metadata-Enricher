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
      "wc"->"0","wa"->"0","wd"->"0.4","ws"->"0.6")
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
      if (matchType.equalsIgnoreCase("HIGH")) score = 5
      else if (matchType.equalsIgnoreCase("GOOD")) score = 4
      else if (matchType.equalsIgnoreCase("MEDIUM")) score = 3
      else if (matchType.equalsIgnoreCase("LOW")) score = 2
    }
    else if (service.equalsIgnoreCase("recommender")) {
      if (matchType.equalsIgnoreCase("high pref")) score = 5
      else score = 3
    }
    else if (service.equalsIgnoreCase("bioportal")) {
      if (matchType.equalsIgnoreCase("high prefLabel")) score = 5
      else score = 3
    }
    else if (service.equalsIgnoreCase("ols")) {
      if (matchType.startsWith("PREF")) {
        if (matchType.contains("-")) {
          val lscore = matchType.split("-")
          println("dentro ols  "+matchType)
          score = 5 - lscore(1).drop(1).toInt
        }
        else score = 5
      }
      else if (matchType.startsWith("SYN")) {
        if (matchType.contains("-")) {
          val lscore = matchType.split("-")
          println("dentro ols  "+matchType)
          score = 3 - lscore(1).drop(1).toInt
        }
        else score = 3
      }
      else score = 1
    }
    score
  }

  def calculate_ontology_score(): Unit = {
    var ontologies: Seq[String] = List()
    var insert: Seq[(String,String,String)] = List()
    var score: Seq[Double] = List()
    val tissue_onto_recsys = db_handler.get_ontology_by_type("tissue")
    val disease_onto_recsys = db_handler.get_ontology_by_type("disease")
    val cellline_onto_recsys = db_handler.get_ontology_by_type("cell_line")

    println("inizio")
    main.get_timestamp()
    for (onto <- tissue_onto_recsys) {
      println("onto "+onto)
      val terms = db_handler.get_term_by_ontology(onto, "tissue")
      var recsys_score = 0.0
      for (term <- terms){
        println("term "+term)
        recsys_score = get_recommender_score(term,onto)
        if(recsys_score != 0.0)
          score :+= recsys_score
      }
      val average = score.foldLeft((0.0, 1)) ((acc, i) => (acc._1 + (i - acc._1) / acc._2, acc._2 + 1))._1
      println(average)
      insert :+= (onto,"tissue",average.toString)
    }
    println("ok tissue")
    main.get_timestamp()
    db_handler.ontology_score_insert(insert)

    insert = List()
    for (onto <- disease_onto_recsys) {
      val terms = db_handler.get_term_by_ontology(onto, "disease")
      for (term <- terms){
        score :+= get_recommender_score(term,onto)
      }
      val average = score.foldLeft((0.0, 1)) ((acc, i) => (acc._1 + (i - acc._1) / acc._2, acc._2 + 1))._1
      insert :+= (onto,"disease",average.toString)
    }
    println("ok disease")
    main.get_timestamp()
    db_handler.ontology_score_insert(insert)

    insert = List()
    for (onto <- cellline_onto_recsys) {
      val terms = db_handler.get_term_by_ontology(onto, "cell_line")
      for (term <- terms){
        score :+= get_recommender_score(term,onto)
      }
      val average = score.foldLeft((0.0, 1)) ((acc, i) => (acc._1 + (i - acc._1) / acc._2, acc._2 + 1))._1
      insert :+= (onto,"cell_line",average.toString)
    }
    println("ok cell line")
    main.get_timestamp()
    db_handler.ontology_score_insert(insert)
  }

  def calculate_score(): Unit = {
    val range = db_handler.get_db_lenght()
    var result: Seq[List[String]] = List()
    val f = new File("out.csv")
    main.get_timestamp()
    for (i <- 1 to range){
      val a = db_handler.get_onto_service_termtype(i)
      val onto_score = db_handler.get_onto_score(a._1, a._3)
      val match_score = get_match_score(i, a._2)
      val score = onto_score.toDouble + match_score.doubleValue

      println(i+"\t"+onto_score+" + "+match_score+" = "+score)
      result :+= List(i.toString, score.toString)
    }
    main.get_timestamp()
    val writer = CSVWriter.open(f)
    writer.writeAll(result)
  }

  def update_score_db(): Unit ={
    val f = new File("out.csv")
    val reader = CSVReader.open(f)

    val res = reader.all()

    for (i <- res.indices){
      db_handler.update_score(res(i).head.toInt,res(i)(1).toDouble)
    }

  }
}