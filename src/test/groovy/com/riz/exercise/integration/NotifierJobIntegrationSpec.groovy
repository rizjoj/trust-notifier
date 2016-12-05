package groovy.com.riz.exercise.integration

import com.mongodb.BasicDBObject
import com.mongodb.MongoClient
import com.riz.exercise.jobs.NotifierJob
import com.riz.exercise.TestApplication
import com.riz.exercise.model.SFDCServerInstance
import com.riz.exercise.persistence.ServerInstanceRepository
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import org.bson.Document
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Created by rizjoj on 12/3/16.
 */
class NotifierJobIntegrationSpec extends Specification {
    @Shared MongoClient mongoClient
    private static final MongodStarter starter = MongodStarter.getDefaultInstance()
    @Shared private MongodExecutable mongodExe
    @Shared private MongodProcess mongod
    @Shared private ConfigurableApplicationContext context

    def instances =
            [new SFDCServerInstance(key:'CS62', location:'NA', environment:'sandbox', releaseVersion:'Winter \'17 Patch 15', status:'OK'),
             new SFDCServerInstance(key:'CS85', location:'EU', environment:'sandbox', releaseVersion:'Winter \'17 Patch 15',  status:'OK'),
             new SFDCServerInstance(key:'NA19', location:'NA', environment:'production',releaseVersion:'Winter \'17 Patch 14.5',status:'OK'),
             new SFDCServerInstance(key:'EU7', location:'EU', environment:'production',releaseVersion:'Winter \'17 Patch 14.5',status:'OK')]

    def startMongoEmbeddedMemory() {
        mongodExe = starter.prepare(new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(27017, Network.localhostIsIPv6()))
                .build())
        mongod = mongodExe.start()
    }

    def setupSpec() {
        System.setProperty("spring.profile.active", "test")

        startMongoEmbeddedMemory()
        mongoClient = new MongoClient("localhost", 27017)

        Future future = Executors.newSingleThreadExecutor().submit(
                new Callable() {
                    @Override
                    ConfigurableApplicationContext call() throws Exception {
                       return (ConfigurableApplicationContext) SpringApplication.run(TestApplication.class)
                    }
                }
        )
        context = future.get(60, TimeUnit.SECONDS)


    }

    def stopMongoEmbedded() {
        mongod.stop()
        mongodExe.stop()
    }

    def cleanupSpec() {
        context.close()
        stopMongoEmbedded()
    }

    def setup() {
        mongoClient.getDatabase("test").getCollection("serverInstances").drop()
        mongoClient.getDatabase("test").getCollection("subscribers").drop()
    }


    def "Test embedded mongodb"() {
        setup:
        mongoClient.getDatabase("something").getCollection("somethingelse").drop()
        when:
        def db = mongoClient.getDatabase("something")
        def collection = db.getCollection("testCol")
        collection.insertOne(new Document("name", "test").append("value", "test123"))
        then:
        collection.find().first().get("name") == "test"
    }


    def "Test application context with embedded mongo"() {
        setup: "Add a server instance to embedded mongodb"
        def serverInstanceRepository = context.getBean(ServerInstanceRepository.class)
        serverInstanceRepository.save(
            new SFDCServerInstance(
                key:"AP1",
                location:"APAC",
                environment:"production",
                releaseVersion:"Winter '17 Patch 14.5",
                status:"OK"))

        when: "Embedded mongo collection is fetched"
        def db = mongoClient.getDatabase("test")
        def collection = db.getCollection("serverInstances")
        then: "Contains the service instance from setup"
        collection.find().first().get("key") == "AP1"
    }


    def "Test notifier with actual remote service - end to end"() {
        setup:
        def service = context.getBean(NotifierJob.class)
        when: "Notifier's execute is invoked"
        def db = mongoClient.getDatabase("test")
        def collection = db.getCollection("serverInstances")
        service.execute()
        then: "Local instances are populated with those fetched from remote call"
        collection.count() > 0
    }


    def "Test notifier with mock remote service"() {
        setup:
        def service = context.getBean(NotifierJob.class)
        service.restTemplate = Mock(RestTemplate)
        service.restTemplate.getForObject(_, _) >> (SFDCServerInstance[])instances

        when: "Notfier's execute is invoked"
        def db = mongoClient.getDatabase("test")
        def collection = db.getCollection("serverInstances")
        service.execute()
        then: "Mongo now contains the server instances fetched from remote service call"
        collection.count() == 4
        collection.count(new BasicDBObject('key', 'CS62')) == 1
        collection.count(new BasicDBObject('key', 'CS85')) == 1
        collection.count(new BasicDBObject('key', 'NA19')) == 1
        collection.count(new BasicDBObject('key', 'EU7')) == 1
        collection.count(new BasicDBObject('key', 'CS7')) == 0 // Some negative tests
        collection.count(new BasicDBObject('key', 'AP1')) == 0 // Some negative tests
    }


    def "Test notifier updates status"() {
        setup: "Add test server instances to local repository and mock remote call"
        def serverInstanceRepository = context.getBean(ServerInstanceRepository.class)
        serverInstanceRepository.save(new SFDCServerInstance(id:"1", key:"EU7", status:"MINOR_INCIDENT_CORE"))
        serverInstanceRepository.save(new SFDCServerInstance(id:"2", key:"CS62", status:"OK"))

        def service = context.getBean(NotifierJob.class)
        service.restTemplate = Mock(RestTemplate)
        service.restTemplate.getForObject(_, _) >> (SFDCServerInstance[])instances

        when: "local server instance has status MINOR_INCIDENT_CORE"
        def db = mongoClient.getDatabase("test")
        def collection = db.getCollection("serverInstances")
        then: "check mongo status field is MINOR_INCIDENT_CORE"
        collection.count() == 2
        collection.find(new BasicDBObject('key', 'EU7')).first().get('status') == 'MINOR_INCIDENT_CORE'

        when: "notifier's execute method is called"
        service.execute()
        then: "mongo status field is updated with value from remote call"
        collection.count() == 4
        collection.find(new BasicDBObject('key', 'EU7')).first().get('status') == 'OK'
    }


}