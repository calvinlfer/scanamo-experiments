import java.util.UUID

import cats.data.Xor
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.gu.scanamo._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.syntax._
import com.gu.scanamo.update.SetExpression

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps

val asyncClient = new AmazonDynamoDBAsyncClient(new BasicAWSCredentials("dev", "dev"))
asyncClient.setEndpoint("http://localhost:8000")

implicit val ec = scala.concurrent.ExecutionContext.global

case class User(memberId: UUID, firstName: String, lastName: String, telephone: String)

// set up DynamoDB conversions for UUID
// use coercedXMap when there is a possibility that exceptions may occur during conversion
implicit def uuidDynamoFormat: DynamoFormat[UUID] =
DynamoFormat.coercedXmap[UUID, String, IllegalArgumentException] {
  // Dynamo => Scala
  stringUUID => UUID.fromString(stringUUID)
} {
  // Scala => Dynamo
  uuid => uuid.toString
}

val usersTable = Table[User]("users")

// Free Monad (create instructions for interpreter)
// instructions
val putInstruction = usersTable.put {
  User(UUID.nameUUIDFromBytes("Calvin".getBytes), "Calvin", "Fernandes", "647-444-4444")
}

// ae7a9f31-f296-3fd4-9350-56cfa158fa3a is "Calvin"
val fetchCalvinInstruction = usersTable.get('memberId -> "ae7a9f31-f296-3fd4-9350-56cfa158fa3a")

val instructions = for {
  _             <-  putInstruction
  optionXorUser <-  fetchCalvinInstruction
} yield optionXorUser

// Submit instructions to interpreter to execute instructions
val futureResult = ScanamoAsync.exec(asyncClient)(instructions)

// Deal with the results of the computation
val optXorCalvin = Await.result(futureResult, 5 seconds)

optXorCalvin.fold("I couldn't find you :-(") { xor =>
  xor.fold(
    (left: DynamoReadError) => s"oh no, couldn't deserialize $left",
    (right: User) => s"$right"
  )
}

case class UpdateUser(memberId: UUID,
                      firstName: Option[String],
                      lastName: Option[String],
                      telephone: Option[String])

val up = UpdateUser(UUID.nameUUIDFromBytes("Calvin".getBytes),
  Some("Calvin"), Some("Fernandes"), Some("647-444-4444")
)

val updateExpressions = List(
  up.firstName.map(fN => set('firstName -> fN)),
  up.lastName.map(lN => set('lastName -> lN)),
  up.telephone.map(t => set('telephone -> t))
).flatten

val instr =
  set('firstName -> "Calvin") and
  set('lastName -> "Fernandes") and
  set('telephone -> "647-444-6540")



//// Instruction to insert a new record by doing an update,
//// Note: if it's not there then it will create it and if there then it will edit it
//val newUserViaUpsertInstruction = usersTable.update(
//  'memberId -> "bb7b9f31-b296-3bb4-1350-56bbb158bb3b",
//  set('collectorNumber -> "900001") and
//    set('coolness -> 10)
//)
//
//// Instruction to update existing user
//val updateCalvinInstruction = usersTable.update(
//  'memberId -> "ae7a9f31-f296-3fd4-9350-56cfa158fa3a",
//  set('collectorNumber -> "9090909090") and
//    set('coolness -> 9)
//)
//
//// Compose individual instructions and sequence them
//// Nothing executed yet
//val moreInstructions = for {
//  _ <- newUserViaUpsertInstruction
//  _ <- updateCalvinInstruction
//  users <- usersTable.scan()
//} yield users
//
//// Execute instructions by submitting them to interpreter for execution
//val anotherFutureResult = ScanamoAsync.exec(asyncClient)(moreInstructions)
//
//val listXor: List[Xor[DynamoReadError, User]] = Await.result(anotherFutureResult, 5 seconds)
//
//listXor.map(eachXor =>
//  eachXor.fold(left => "couldn't deserialize", right => s"user: $right")
//)