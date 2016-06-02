
/*
Problem:
- we want to keep Slick out of the service layer, and only reference it in the repo layer.
- but we want to compose methods from repositories inside transactions,
  which we can't do once we have gone to Future.
*/

import scala.concurrent.Future

object Example {

  // We can compose around this abstraction on an action:

  case class RepoAction[+T](private val action: slick.dbio.DBIO[T]) {

    // Inside we can work in terms of Slick
    import slick.driver.H2Driver.api._
    import scala.concurrent.ExecutionContext

    val db = Database.forConfig("example") // Would be injected

    def run(): Future[T] = db.run(action)
    def runTransactionally(): Future[T] = db.run(action.transactionally)

    def andThen[R](that: => RepoAction[R]): RepoAction[R] =
      RepoAction(this.action andThen that.action)

    def flatMap[R](f: T => RepoAction[R])(implicit ec: ExecutionContext): RepoAction[R] =
      RepoAction(action.flatMap(t => f(t).action))

    def map[R](f: T => R)(implicit ec: ExecutionContext): RepoAction[R] =
      RepoAction(action map f)
  }

  // Example entities as a demonstration...
  case class PersonPK(value: Long)
  final case class Person(name: String, id: PersonPK = PersonPK(0L))
  final case class Address(street: String, occupier: Option[PersonPK] = None)

  object Repositories {

    // Inside the repository we can use all things Slick as normal
    import slick.driver.H2Driver.api._

    implicit val pkMapper = MappedColumnType.base[PersonPK, Long](_.value, PersonPK.apply)

    final class PersonTable(tag: Tag) extends Table[Person](tag, "person") {
      def id   = column[PersonPK]("id", O.PrimaryKey, O.AutoInc)
      def name = column[String]("name")
      def *    = (name, id) <> (Person.tupled, Person.unapply)
    }

    final class AddressTable(tag: Tag) extends Table[Address](tag, "address") {
      def street   = column[String]("street")
      def occupier = column[Option[PersonPK]]("occupier")
      def *        = (street, occupier) <> (Address.tupled, Address.unapply)
    }

    lazy val people = TableQuery[PersonTable]
    lazy val addresses = TableQuery[AddressTable]

    // To create the schema and insert a record
    def init(): RepoAction[Int] = {
      RepoAction(
        (people.schema ++ addresses.schema).create andThen
          (people += Person("Mrs Hudson"))
      )
    }

    // An automatic converstion from DBIO to RepoAction
    implicit def intoRepoAction[T](dbio: DBIO[T]): RepoAction[T] =
      RepoAction(dbio)

    // Example repositories, that use Slick, but produce our own RepoAction results

    class PersonRepo {
      def find(name: String): RepoAction[Option[Person]] =
        people.filter(_.name === name).result.headOption
    }

    class AddressRepo {

      def moveIntoAddress(p: Person, street: String): RepoAction[Int] =
        addresses += Address(street, Some(p.id))

      def all(): RepoAction[Seq[Address]] = addresses.result

    }
  }

  // Inside a service we talk in terms of Repositories only. No Slick details here...
  case class PretendService(people: Repositories.PersonRepo, places: Repositories.AddressRepo) {

    import scala.concurrent.ExecutionContext.Implicits.global

    def occupy(name: String, address: String): Future[Int] = {

      // We can work across repositories....
      val action = for {
        _        <- Repositories.init()
        person   <- people.find(name)
        rowCount <- places.moveIntoAddress(person getOrElse Person("Nobody"), address)
      } yield rowCount

      action.runTransactionally()
    }

    def everywhere(): Future[Seq[Address]] =
      places.all.run()
  }

  // Putting it all together...
  def main(args: Array[String]): Unit = {

    import scala.concurrent.Await
    import scala.concurrent.duration._

    val service = new PretendService(
      new Repositories.PersonRepo(),
      new Repositories.AddressRepo()
    )

    val insertResult = Await.result(service.occupy("Mrs Hudson", "221B Baker Street"), 5 seconds)
    println(s"Running insert service produces: $insertResult")

    import scala.concurrent.ExecutionContext.Implicits.global
    service.everywhere().foreach(println)
  }

}

