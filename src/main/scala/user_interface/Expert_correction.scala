package user_interface

import Enricher.DBCon.{db_handler, default_values, expert_preference_type}
import Utils._

import scala.io.StdIn

object Expert_correction {

  def correction_routine(): Unit = {
    display_prompt()
    val user_selection = get_choice(2)
    var flag = user_selection == "1"
    while (flag) {
      val tmp = Utils.choose_table_column()
      val table = tmp._1
      val column = tmp._2
      println(table)
      println(column)

      println("Input value to update")
      var value = StdIn.readLine()
      println
      while (db_handler.get_value_info(value,table,column).isEmpty){
        println("Value not valid")
        val options = db_handler.get_suggestions_raw(value,table,column)
        if (options.nonEmpty) {
          println("Select one from these options")
          for (i <- options.indices) {
            println(i + 1 + " - " + options(i))
          }
          value = options(Utils.get_choice(options.length).toInt - 1)
          println("value chosen: " + value)
        }
        else {
          println("No suggestions found")
          println("Please input another value")
          value = StdIn.readLine()
          println
        }
      }

      val source_code = input_source_code()
      db_handler.update_tid(value,None,table,column)
      val tuples = db_handler.get_value_info(value,table,column)

      for ((table_name, column_name) <- tuples) {
        db_handler.insert_user_changes(expert_preference_type(default_values.int,table_name, column_name, value, source_code._1, source_code._2))
      }
      println("Do you wish to update another value?")
      println("y/n")

      var choice = StdIn.readLine()
      while (!choice.equalsIgnoreCase("y") && !choice.equalsIgnoreCase("n")){
        println("unknown command")
        println("type y/n")

        choice = StdIn.readLine()
      }

      if(choice.equalsIgnoreCase("y")){
        flag = true
      }
      else if(choice.equalsIgnoreCase("n"))
        flag = false
    }
  }

  def display_prompt(): Unit = {
    println("1 - Start correction")
    println("2 - Go back to home page")
  }
}