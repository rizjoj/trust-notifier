/**
 * Created by rizjoj on 12/2/16.
 */

package groovy.com.riz.exercise.tdd

import com.google.common.collect.ArrayListMultimap
import com.riz.exercise.jobs.NotifierJob
import com.riz.exercise.model.SFDCServerInstance
import com.riz.exercise.model.Subscriber
import com.riz.exercise.persistence.ServerInstanceRepository
import com.riz.exercise.persistence.SubscriberRepository
import org.springframework.mail.javamail.JavaMailSender
import spock.lang.Specification

import javax.mail.internet.MimeMessage

class NotifierJobSpec extends Specification {

    def service, instances, subscribers

    def setup() {
        service = new NotifierJob()

        instances =  [new SFDCServerInstance(key: 'CS85', status: 'OK'),
                      new SFDCServerInstance(key: 'CS62', status: 'OK'),
                      new SFDCServerInstance(key: 'NA16', status: 'OK'),
                      new SFDCServerInstance(key: 'NA01', status: 'OK')]

        subscribers = [new Subscriber(id: '1', firstname: 'Alice', lastname: 'Andrews', email: 'alice@andrews.com', servers: ['CS62', 'NA16']),
                       new Subscriber(id: '2', firstname: 'Bob', lastname: 'Baker', email: 'bob@baker.com', servers: ['NA16', 'CS85'])]

    }


    def "test getting existing server instances from data store"() {
        setup:
        service.serverInstanceRepository = Mock(ServerInstanceRepository)
        service.serverInstanceRepository.findAll() >> instances

        when: "getExistingServerInstances is called"
        def result = service.getExistingServerInstances()

        then: "return a map of instances by their instance name"
        result == ["CS85": instances[0], "CS62": instances[1], "NA16": instances[2], "NA01": instances[3]]
    }


    def "test remote dataset updates local dataset"() {
        def remoteServerInstances = [instances[0], instances[1]]
        def localServerInstances = ["CS62": new SFDCServerInstance(id: '1', key: 'CS62', status: 'MINOR_INCIDENT_CORE'),
                                    "CS85": new SFDCServerInstance(id: '2', key: 'CS85', status: 'OK')]

        setup:
        service.serverInstanceRepository = Mock(ServerInstanceRepository)
        service.serverInstanceRepository.save(_) >> _

        when:
        def updatedServerInstances = service.upsertChangedInstances(remoteServerInstances, localServerInstances)

        then:
        updatedServerInstances.size() == 1
        updatedServerInstances[0] == new SFDCServerInstance(id: '1', key: 'CS62', status: 'OK')
    }


    def "test remote dataset doesn't updates local dataset for brand new instances"() {
        def remoteServerInstances = [new SFDCServerInstance(key: 'CS62', location: 'NA', status: 'OK')]
        def localServerInstances = ["CS85": new SFDCServerInstance(id: '2', key: 'CS85', status: 'OK')]

        setup:
        service.serverInstanceRepository = Mock(ServerInstanceRepository)
        service.serverInstanceRepository.save(_) >> _

        when:
        def updatedServerInstances = service.upsertChangedInstances(remoteServerInstances, localServerInstances)

        then:
        updatedServerInstances.size() == 0
    }


    def "test fetch subscribers based on server instance names"() {
        setup:
        service.subscriberRepository = Mock(SubscriberRepository)
        service.subscriberRepository.findByServers('NA16') >> [subscribers[0], subscribers[1]]
        service.subscriberRepository.findByServers('CS62') >> [subscribers[0]]
        service.subscriberRepository.findByServers('CS85') >> [subscribers[1]]
        service.subscriberRepository.findByServers('NA01') >> []

        when: "subscribers for instance0/CS85 is sought"
        def map = service.getSubscriberServerMap([instances[0]])
        then: "return only subscriber1/Bob"
        map.keySet().size() == 1
        map.get(subscribers[1]) == [instances[0]]

        when: "subscribers for instance1/CS62 is sought"
        map = service.getSubscriberServerMap([instances[1]])
        then: "return only subscriber0/Alice"
        map.keySet().size() == 1
        map.get(subscribers[0]) == [instances[1]]

        when: "subscribers for instance2/NA16 is sought"
        map = service.getSubscriberServerMap([instances[2]])
        then: "return subscriber0/Alice as well as subscriber1/Bob"
        map.keySet().size() == 2
        map.get(subscribers[0]) == [instances[2]]
        map.get(subscribers[1]) == [instances[2]]

        when: "subscribers for multiple instances (instance0,1,2) is sought"
        map = service.getSubscriberServerMap([instances[0], instances[1], instances[2]])
        then: "ensure that corresponding subscribers and their instances match"
        map.keySet().size() == 2
        map.get(subscribers[0]).sort() == [instances[1], instances[2]]
        map.get(subscribers[1]).sort() == [instances[0], instances[2]]

        when: "subscribers for instance3/NA01 is sought"
        map = service.getSubscriberServerMap([instances[3]])
        then: "return no subscribers"
        map.keySet().size() == 0
    }


    def "test emails sent based on number of subscribers"() {
        def multimap = ArrayListMultimap.create()
        multimap.put(subscribers[0], instances[0])
        multimap.put(subscribers[0], instances[1])
        multimap.put(subscribers[1], instances[2])
        multimap.put(subscribers[1], instances[3])

        setup:
        service.javaMailSender = Mock(JavaMailSender)
        service.javaMailSender.createMimeMessage() >> Mock(MimeMessage)
        service.emailSender = "a.b.com"
        service.emailSubject = "subject"

        when:
        service.sendEmails(multimap)
        then:
        2 * service.javaMailSender.send(_)
    }

    def "test email message body string"() {
        when: "email message for a user with name Riz Mappillai and 2 instances is requested"
        String message = service.generateEmailMessage("Riz Mappillai", instances)

        then: "return a string informing that user that 2 instances status' have changed"
        message == """Hello Riz Mappillai,\r
\r
There has been a change of status in 4 SFDC server instances you are subscribing to.\r
\r
Here are the current statuses of those servers that have changed:\r
\r
CS85 -> OK\r
CS62 -> OK\r
NA16 -> OK\r
NA01 -> OK\r
\r
Thank you."""
    }


}