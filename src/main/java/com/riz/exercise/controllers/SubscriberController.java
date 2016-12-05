package com.riz.exercise.controllers;

import com.riz.exercise.model.Subscriber;
import com.riz.exercise.persistence.SubscriberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Created by rizjoj on 12/3/16.
 */

@Controller
public class SubscriberController {

    @Autowired
    private SubscriberRepository subscriberRepository;

    @RequestMapping("/subscribers")
    public String subscribers(Model model) {
        model.addAttribute("subscriberList", subscriberRepository.findAll());
        return "subscribers";
    }

    @RequestMapping(value = "/addSubscriber", method = RequestMethod.POST)
    public String addSubscriber(@ModelAttribute Subscriber subscriber) {
        if ((!isNullOrEmpty(subscriber.getFirstname()) || !isNullOrEmpty(subscriber.getLastname())) &&  // First or Last name required
                !isNullOrEmpty(subscriber.getEmail()) && // Email required
                subscriber.getServers().size() > 0) { // At least 1 server required

            List<Subscriber> existingSubscribers = subscriberRepository.findByEmail(subscriber.getEmail());
            if (existingSubscribers.size() > 0) {
                subscriber.setId(existingSubscribers.get(0).getId());
            }

            subscriberRepository.save(subscriber);
        }

        return "redirect:subscribers";
    }
}
