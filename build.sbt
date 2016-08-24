name := "dynamodb-experiments"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  Seq(
    "com.gu"                %% "scanamo"        % "0.7.0",
    "org.typelevel"         %% "cats"           % "0.7.0"
  )
}