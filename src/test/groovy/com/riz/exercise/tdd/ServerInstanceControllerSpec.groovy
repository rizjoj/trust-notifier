package groovy.com.riz.exercise.tdd

import com.riz.exercise.controllers.ServerInstanceController
import com.riz.exercise.model.SFDCServerInstance
import com.riz.exercise.persistence.ServerInstanceRepository
import org.springframework.ui.ExtendedModelMap
import spock.lang.Specification

/**
 * Created by rizjoj on 12/3/16.
 */
class ServerInstanceControllerSpec extends Specification {

    def controller

    def setup() {
        controller = new ServerInstanceController()
        controller.serverInstanceRepository = Mock(ServerInstanceRepository.class)
    }

    def "tdd: create /servers GET request"() {
        when:
        controller.serverInstanceRepository.findAll() >> _
        def result = controller.servers(new ExtendedModelMap())

        then:
        result == "servers"
    }

    def "tdd: create /updateServerStatus POST endpoint"() {
        when:
        controller.serverInstanceRepository.findByKey('EU5') >> [new SFDCServerInstance(key:'EU5', status:'OK')]
        def result = controller.updateServerStatus(new SFDCServerInstance(key:'EU5', status:'NOT_OK'))

        then:
        result == "redirect:servers"
        1 * controller.serverInstanceRepository.save(_)
    }

    def "tdd: do not post if server key doesn't match"() {
        when:
        controller.serverInstanceRepository.findByKey('XYZ') >> []
        controller.updateServerStatus(new SFDCServerInstance(key:'XYZ', status:'NOT_OK'))

        then:
        0 * controller.serverInstanceRepository.save(_)
    }

    def "tdd: do not post if server key not supplied"() {
        when:
        controller.updateServerStatus(new SFDCServerInstance(status:'NOT_OK'))

        then:
        0 * controller.serverInstanceRepository.save(_)
    }
}
