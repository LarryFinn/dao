# DAO DEEP DIVE #
## Important Functions ##
### Read ###
**read** - typical read function, returns a `Future[Seq[Model]]`.  It takes an optional parameter of an anonymous 
function that allows you to add arbitrary filters, sorts, etc... to the underlying query.  The anonymous function
  gets the underlying slick query as a parameter and needs to return a slick query.  For example:
```scala
read(q => q.filter(_.name === "meow").take(1))
```
**readById** - read objects by a single id or set of ids.  Will return a `Future[Option[Model]]` or 
`Future[Seq[Model]]` accordingly  
**readByIdRequired** - read objects by single id or set of ids.  Throw exception if any id is invalid.  Will return a
`Future[Model]` or `Future[Seq[Model]]` accordingly.
### Create ###
**create** - create object or objects and returns ids.  Takes in a single object or sequence of objects.  
Returns a `Future[I]` or `Future[Seq[I]]` accordingly, with I being the class of the IdType.  
**createAndRead** - create object or objects and return the persisted objects.  Takes in a single object or sequence
of objects.  Returns a `Future[YourModel]` or `Future[Seq[YourModel]]` accordingly.
### Update ###
**update** - update object or objects and returns ids.  This a full replacement of existing rows based on the id of the
input. Takes in a single object or sequence of objects.  Returns a `Future[I]` or `Future[Seq[I]]` accordingly, 
with I being the class of the IdType.  
**updateAndRead** - update object or objects and return the persisted objects.  This a full replacement of existing 
rows based on the id of the input. Takes in a single object or sequence of objects.  Returns a `Future[YourModel]` 
or `Future[Seq[YourModel]]` accordingly.
### Delete ###
**delete** - Delete an object or set of objects.  Takes in a single id or Seq of ids.  Returns a `Future[Int]`, 
representing the count of rows deleted.

## Call Path ##
### Read ###
The read functions are pretty simple.  `read` generally calls `readAction`.  `readAction` does two things:
1. Calls `readQuery`, which returns the slick query to select from the table WITH default filters
2. Runs the optional extra query options function on top of the query returned from #1

The result of `readAction` is a `DBIOAction` that gets pumped through `db.run` transactionally 
to actually run the query and return a result as a future.

### Create ###
Create does 3 things.
1. Calls `processPreCreate` on the input.  The default implementation just returns the input, but this could set 
default values / make sure users don't set values stupidly.  
2. Calls createAction with the output of #1
3. Calls `db.run` transactionally on the result of #2 

createAction does 3 things
1. Validates the input.  It does this by calling the `validateCreate` function.  If this validation has any failures,
the next step is skipped.
2. Calls `createQuery` on the input.  This does an insert and returns the id.
3. If the insert succeeds, log the transaction.  If the validation failed or insert fails, throw the exception upward.

### Update ###
Update does 4 things.
1. Queries the original based on the input id.  If the query fails then the call fails
2. Runs `processPreUpdate` on the input and original
3. Takes the output of #1 and #2 and runs it through `updateAction`
4. Calls `db.run` transactionally on the result of #3

updateAction does 3 things
1. Validates the input.  It does this by calling the `validateUpdate` function.  If this validation has any failures,
the next step is skipped.
2. Calls `updateQuery` on the input.  This does an insert and returns the id.
3. If the update succeeds, log the transaction.  If the validation failed or update fails, throw the exception upward.

### Delete ###
Delete does 2 things. 
1. Runs `deleteAction` on the input id(s)
2. Calls `db.run` transactionally on the result of #2

deleteAction does 3 things
1. Try to get the original object by calling `readAction`.  If the original object does not exist, fail.
2. Call deleteQuery on the input
3. If the delete succeeds, log the transactions

## Interesting Functions ##
### updateActionFunctional ###
Underneath the hood, all update functions call updateActionFunctional.  This function takes 3 inputs.
1. **original: V** - The original object from the database
2. **validationInputOriginal: (NewAndOriginal[V]) => DBIOAction[FormValidatorMessageSeq, NoStream, Effect.Read]** - A 
lambda function that takes in a NewAndOriginal case class (which is a wrapper around the input and original object)
and returns a DBIOAction of FormValidatorMessageSeq.  This allows you to write any arbitrary validator for a
specialized function and the validator will get the new and original objects as input.
3. **updateFunction: V => V** - A lamba function that takes in a model and returns a model.  The 
`updateActionFunctional` will call this lambda function with the original object from the db.  This allows the
developer to just change specific values of an object without needing to have the whole new object.  For example, if 
a user wants to write a function to flip the is_active flag they could do it easily with this lambda.
### addCreateTransaction (Update and Delete too) ###
This function gets called after successful transactions.  These functions first pull in the implicit function defined
on the model that converts a model to JSON.  It then calls `write` on the injected transactionLogger instance with
the context, operation, class type, inputs, etc...  The transaction logger converts the input and original to JSON
for logging.



