# Trust Notifier

A Notification Application that polls server instances from Salesforce.com's
[Trust API](https://api.status.salesforce.com/v1/instances) and informs subscribers
via email whether any server instances they have subscribed to have had a change in status.

## Prerequisites

On the machine where your are running this application you will need the following:

- MongoDB 3.x
- Java JDK 8

## Installation <a name="installation"></a>

1. Update the folllowing properties in the `src/main/resources/application.properties` file:
    - `spring.mail.username` This is the email account notifications will be sent from, same as above
    - `spring.mail.password` This is email password for the above account
    - `com.riz.exercise.application.email.sender` This is the email reply-to address (usually same as the above sender 
    email id)
    - Other `spring.mail.*` properties that corresponding with your SMTP server settings. Leave as is if you will be 
    sending emails from your own gmail.com email account.
    
2. By default the notifier job runs every quarter of the hour, on the hour 
(e.g.: 1:30, 1:45, 2:00, 2:15 ...).
 If you don't want to wait that long you can update the 
 `com.riz.exercise.application.scheduler.cron` property.


#### Compile

Go to the `trust-notifier` folder (where you should see the `pom.xml` file) and run `./mvnw clean install`:
```
cd trust-notifier
./mvnw clean install
```

`mvnw` is a maven wrapper that will download and run maven if you don't have it already installed. You may also use 
`mvn clean install` if you already have maven on your machine.

#### Startup

Once compiled, from the `trust-notifier` directory, run:
```
./mvnw spring-boot:run
```
Note: When starting up, if you see the error: `com.mongodb.MongoSocketOpenException: Exception opening socket` it means 
either your mongod server isn't running, or the application is unable to connect to it. If it is running, also check 
that the mongo server port is the default: `27017` as the application's mongo client may be connecting to it from that 
port.

## Usage

Once startup completes successfully there are two pages you can view:
[Subscribers](#subscribers) and [Servers](#servers).

#### Subscribers<a name="subscribers"></a>
```
http://localhost:8080/subscribers
```
Displays a list of Subscribers saved in the local datastore.

##### Adding new subscribers
On the page, below the list of subscribers is a form. Add the new subscriber's first name, last name and email. The 
servers field accepts server instance keys in CSV (comma separated value) form. E.g.: `NA1, AP1, EU7`

##### Updating existing subscribers
Existing subscribers can be updated by using their email id as the key. If the email id provided matches that of a 
subscriber in the list, a new subscriber will not be added, rather all fields of the subscriber matching that email id 
will be updated, including the server list.

###### Deleting existing subscribers
Not currently supported

##### Subscriber form validation
This form has required fields. If form validation fails, pressing the `Submit` button will not result in an add or an 
update; no error message is currently provided. For an add/update to be successful the form must have:
- Either the first name, last name or both. First name and last name cannot **both** be blank.
- Email id is required.
- One or more server instance keys must be provided. Cannot be empty.

#### Servers <a name="servers"></a>
```
http://localhost:8080/servers
```
Displays a list of Salesforce.com Server Instances saved in the local datastore. This cannot be user added although 
server instances already added can have their status values updated (to test email notification)

##### Updating server status

Once a server has been fetched from the salesforce.com's trust api, it's status can be updated using the form below the 
server list (scroll all the way to the bottom).

##### Subscriber update form validation

This form has two fields: 1) Server key and 2) Status.

The status field can be anything, including blank. However for an update to take place, validation on the Server Key 
field must pass. If form validation fails, pressing the `Submit` button will not result in server status update; no
error message is currently provided.

The following validation rules apply to the server key field:
- The server key field must not be empty.
- The server key field must match an existing value in the table.

#### Receiving Email Notifications

Mostly, the server instances have a status of 'OK'. This makes it hard to see the email notification in action.

To simulate a change in status and the sending of an email notification do the following:
- Go to the subscribers page: [http://localhost:8080/subscribers](http://localhost:8080/subscribers). 
  <br/>Add or update a subscriber. 
Make a note of the server(s) that subscriber subscribes to.
- Go to the servers page: [http://localhost:8080/servers](http://localhost:8080/servers). 
  <br/>Update one or more servers' status.
- Wait for the Notifier Job to run
  <br/>You may want to increase the polling frequency to avoid waiting upto 15 minutes before you can verify the email 
  is sent. See [Installation](#installation)'s Step 2 above to find out how to change and increase the poller frequency.
- Using GMail's SMTP: Receiving an email successfully depends on your SMTP parameters and Email account credentials
  being correct. For example: When using GMail's SMTP server settings, the **sender**'s email will receive an email 
  from GMail asking to reduce the account's security settings to allow emails from `gmail.smtp.com`. The recipient may 
  also receive such a message with action to reduce their security level to accept incoming email from 3rd parties 
  sending emails via smtp.gmail.com.
  
If everything works, you will see an email like:
```
Hello John Smith,

There has been a change of status in 1 SFDC server instances you are subscribing to.

Here are the current statuses of those servers that have changed:

CS62 -> OK
EU5 -> MINOR_INCIDENT_CORE
CS85 -> OK

Thank you.
```

## Developer Notes

##### TDD and Test Cases

This application was developed using TDD with the unit and integration tests written in groovy using the Spock testing
framework.

You can see the tests in the `src/test/groovy` folder and sub-folders.

Integration tests are in the `src/test/groovy/com/riz/exercise/integration` folder. Currently there is one integration
test specification: `NotifiedJobIntegrationSpec`. This test specification uses an embedded mongo db to perform end to
end tests.

Before running this test, if your local mongod server's port is the default: 27017, it helps to turn off your local 
mongod server as the embedded mongod server's port is also the default 27017.

However, sometimes, running the Integration test will result in your local mongod server to terminate, in which case 
simply run the test again (or even a third time) for the integration test specification to run.

##### Frameworks and plugins used
- Spring Boot (with dependencies: Web, Rest, Thymeleaf, Mail, MongoDB)
- Thymeleaf (UI Views)
- Spock and Mockito (Testing)
- Google Guava, Google collections, Lambda expressions

## Possible Enhancements

#### Better User Feedback

- Ability to delete subscribers and servers via the web UI
- Better validation, error reporting and operation result in web UI
- Notification email to distinguish between a newly added server vs. one whose status has changed

#### Better Developer Feedback and Usability

- Manually update server status in the local datastore (to simulate server status change, and thus test email notification) **IMPLEMENTED**
- Manual, ad-hoc invokation of the Notifier Job (say, via an http GET request) for testing
- Run unit tests during compilation, and integration tests during packaging/deployment

#### Better decoupling of code components

- **Mongo/Datastore**: Decouple mongo dependency from the application code via a generic data store interface. This allows multiple additional data access mechanisms to be configured with the application (e.g.: Redis, REST, SQL database, etc) and one to be actively applied to the runtime using, say, the strategy pattern via an application configuration file.

- **Email/Notification**: Decouple the email dependency from the application code via a generic notification interface. This allows multiple notification strategies to be employed and configured in the application (e.g.: PagerDuty, REST API, Message Queues) and used simultaneously using, say, the fan-out messaging pattern. E.g.: In addition to an email, PagerDuty.com's API could be invoked simultaneously to alert DevOps on their pager or phone.
