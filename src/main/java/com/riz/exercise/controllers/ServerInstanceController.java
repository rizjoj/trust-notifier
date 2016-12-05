package com.riz.exercise.controllers;

import com.riz.exercise.model.SFDCServerInstance;
import com.riz.exercise.persistence.ServerInstanceRepository;
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
public class ServerInstanceController {

    @Autowired
    private ServerInstanceRepository serverInstanceRepository;

    @RequestMapping("/servers")
    public String servers(Model model) {
        model.addAttribute("serverList", serverInstanceRepository.findAll());
        return "servers";
    }

    @RequestMapping(value = "/updateServerStatus", method = RequestMethod.POST)
    public String updateServerStatus(@ModelAttribute SFDCServerInstance serverInstance) {
        if (!isNullOrEmpty(serverInstance.getKey())) {
            // Find a server instance from local datastore (mongo) using the server key passed in by user
            List<SFDCServerInstance> existingServers = serverInstanceRepository.findByKey(serverInstance.getKey());
            // Only if one is found (i.e., matches), update the existing record with the status passed in by user
            if (existingServers.size() > 0) {
                SFDCServerInstance serverInstanceToUpdate = existingServers.get(0);
                serverInstanceToUpdate.setStatus(serverInstance.getStatus());
                serverInstanceRepository.save(serverInstanceToUpdate);
            }
        }

        return "redirect:servers";
    }
}
