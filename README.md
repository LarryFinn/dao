# Luna #
Luna makes DAOs
## Introduction ##
So I hear you want to interact with a database and you already use Slick or may want to use Slick.
Slick provides:
* Type safe interface for querying a database
* Asynchronous execution of queries
* Connection pool management
* Functional interface to SQL actions
* Mechanism for converting rows to case classes

However, Slick can be both complicated and limiting.
Slick:
* Is definitely not an ORM and favors towards scala tuples, which can get pretty hard to navigate (like `_._1._2._3`)
* Was written by scala ninjas so the library source can be hard to parse / extend
* Abstracts how/where queries are run via DBIOActions, so it is not obvious if queries are ran in the same connection
or transaction. Slick provides the functionality to force queries to run together but you need to be very careful.
* Favors query building over common actions.  Typical database operations are CRUD based (Create, Read, Update, Delete)
but those abstractions are too high level for Slick to expose

"Proper" development practice stipulates that a DAO (Data Access OBject) should wrap database actions. 
Because Slick is very low level and can be complicated to use, we need some other abstraction to simply provide 
common DAO functionality.

Therefore we built **LUNA** to make creating DAOs easy. 

## Requirements ##
* MySQL/Postgres/H2 Database (probably can easily be extended to support others)
* For each table that you query, you will need the typical slick abstractions.  You will need a case class to represent
the data from each row.  You will also need a "table" class that maps the fields in the table to the fields in the case
class
* Your database table needs some sort of ID.  Either a Long or UUID.

## Components ##
### IdType ###
This is an abstraction around IDs a row might have.  The IdType has two functions that make it like an option because
auto incremented rows might not have an ID before they are created.
Common Prepackaged IdTypes:
* DbLongOptID - Id is a Long and optional (because of autoincrement)
* DbUUID - Id is a UUID stored as a byte array
### DAOTable ###
The "slick table" representation.  This is used by slick to map between rows to models.  You will need to implement
one of these.  There is a table implementation for each database type.  Slick needs to know the database type
for certain query mechanics.
* MySQLDAOTable - Table for MySQL
* PostgresDAOTable - Table for Postgres
* H2DAOTable - Table for H2
* DAODynamicProfileTable - Table that supports runtime injection of the database type.  Useful if you want to run 
tests against H2 but use a different database in production.
### IdModel / DAOModel ##
An IdModel is just a case class that has an id field of some IdType.  The models representing database rows should
inherit from this construct.  You will need to implement one of these.
There is a model implementation for each database type.  Slick needs to know the database type
for certain query mechanics.
* MySQLIdModel - Model for MySQL
* PostgresIdModel - Model for Postgres
* H2IdModel - Model for H2
### The Actual DAO ###
When you implement your DAO, you will be extending some version of the base DAO class.  The base DAO class is a
hierarchy of a bunch of classes, so let's look at it from the bottom up.
#### DefaultFilter ####
A very common use case for a DAO is to have some default filter on every action you take.  Typically, you want the
caller of the DAO to be limited to objects they have access to.  All of the CRUD operations and their derivatives
will utilize the default filters.  These default filters are traits and stackable.
#### DAOQuery ####
DAOQuery will generate slick queries based on default filters and input. Most functions return a `Query` type or some
variation.  Most functions are protected and for internal DAO use.
Common Prepackaged DAOQueries:
* DAOLongIdQuery - DAO Query that uses an optional long for id
* DAOUUIDQuery - DAO Query that uses a UUID for id
These prepackaged classes are necessary because you need to implement different ways of comparing or retrieving ids.
Your DAO class will need to extend one of these.
#### DAOAction ###
ACTION PACKED center of the DAO structure.  CRUD "action" functions are implemented here.  Most functions return a
DBIOAction or some variation, which is Slick's nonblocking representation of some database action.  These can be
stacked into a transaction, or run separately.  The functions are protected and are for internal DAO use.
Create and Update based functions will optionally call a validator.  A validator is a simple function that takes input
(and original if an update) and validate that all the values follow business rules.  The validator can actually return
multiple errors.
The code to log transactions lives in this class.
#### DAO ####
YOU MADE IT!!!111!  This is the class you should be extending to build your DAO.  The functions defined here actually
run the DBIOActions generated by the DAOAction functions.  The proper calls will be chained into a transaction and most
functions return a future.  Returning futures makes perfect sense because service calls return futures, and everything
can be chained nicely.  DAO create and update operations also have optional pre and post processing functions, allowing
the developer to implement default values / values that cannot be overridden once set.
* MySQLDAO - DAO for MySQL
* PostgresDAO - DAO for Postgres
* H2DAO - DAO for H2
Again, you need to mix in one of the DAO Id Query types of DAOLongIdQuery or DAOUUIDQuery

## Examples ##
Check out [test example](test/scala/co/actioniq/slick/example)

## Recommendations ##
Generally, it is a good idea to make some base DAOModel and DAOTable classes that you can reuse everywhere so
that you don't need to remember what to mix in.  So you might make
```
  trait BaseModel extends MySQLIdModel[DbUUID]
  abstract class BaseTable[V <: BaseModel](
    tag: Tag,
    tableName: String,
    schemaName: Option[String] = None
  ) extends MySQLIdTable
  abstract class BaseDAO[T <: BaseTable[V], V <: BaseModel](
    override val db: DBWithLogging,
    override val slickQuery: TableQuery[PlayerTable]
  ) extends MySQLDAO[T, V, DbUUID] with DAOUUIDQuery[T, V, MySQLProfile] 
```

