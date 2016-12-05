package groovy.com.riz.exercise.tdd

import com.riz.exercise.controllers.SubscriberController
import com.riz.exercise.model.SFDCServerInstance
import com.riz.exercise.model.Subscriber
import com.riz.exercise.persistence.ServerInstanceRepository
import com.riz.exercise.persistence.SubscriberRepository
import org.springframework.ui.ExtendedModelMap
import spock.lang.Specification

/**
 * Created by rizjoj on 12/3/16.
 */
class SubscriberControllerSpec extends Specification {

    def controller

    def setup() {
        controller = new SubscriberController()
        controller.subscriberRepository = Mock(SubscriberRepository.class)
    }

    def "tdd: create /servers GET request"() {
        when:
        controller.subscriberRepository.findAll() >> _
        def result = controller.subscribers(new ExtendedModelMap())

        then:
        result == "subscribers"
    }


    def "tdd: create /addSubscriber POST endpoint"() {
        when:
        controller.subscriberRepository.findByEmail('a@b.com') >> []
        def result = controller.addSubscriber(new Subscriber(firstname:'A', lastname:'B', email:'a@b.com', servers:['NA16']))

        then:
        result == "redirect:subscribers"
        1 * controller.subscriberRepository.save(_)
    }

    def "tdd: do not post if first and lastnames aren't provided"() {
        when:
        controller.addSubscriber(new Subscriber(email:'a@b.com', servers:['NA16']))

        then:
        0 * controller.subscriberRepository.save(_)
    }

    def "tdd: post if firstname is provided but lastname is not"() {
        when:
        controller.subscriberRepository.findByEmail('a@b.com') >> []
        controller.addSubscriber(new Subscriber(firstname:'A', email:'a@b.com', servers:['NA16']))

        then:
        1 * controller.subscriberRepository.save(_)
    }

    def "tdd: post if lastname is provided but firstname is not"() {
        when:
        controller.subscriberRepository.findByEmail('a@b.com') >> []
        controller.addSubscriber(new Subscriber(lastname:'B', email:'a@b.com', servers:['NA16']))

        then:
        1 * controller.subscriberRepository.save(_)
    }

    def "tdd: do not post if email is not provided"() {
        when:
        controller.addSubscriber(new Subscriber(firstname:'A', lastname:'B', servers:['NA16']))

        then:
        0 * controller.subscriberRepository.save(_)
    }

    def "tdd: do not post if no servers are provided"() {
        when:
        controller.addSubscriber(new Subscriber(firstname:'A', lastname:'B', servers:[]))

        then:
        0 * controller.subscriberRepository.save(_)
    }

    def "tdd: set mongo id if subscriber already exists"() {
        when:
        controller.subscriberRepository.findByEmail('a@b.com') >> [new Subscriber(id:'1', firstname:'A', lastname:'B', email:'a@b.com', servers:['NA16'])]
        def subscriber = new Subscriber(firstname:'A', lastname:'B', email:'a@b.com', servers:['NA16'])
        controller.addSubscriber(subscriber)

        then:
        subscriber.getId() == '1'
    }

}
