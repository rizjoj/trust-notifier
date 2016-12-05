package com.riz.exercise.jobs;

/**
 * Created by rizjoj on 12/1/16.
 */

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.riz.exercise.model.SFDCServerInstance;
import com.riz.exercise.model.Subscriber;
import com.riz.exercise.persistence.ServerInstanceRepository;
import com.riz.exercise.persistence.SubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  Service class that contacts salesforce.com's REST api at a given frequency (e.g.: every 15 minutes) and notifies
 *  subscribers by email about status changes for those specific server instances that the user have subscribed to
 */
@Service
public class NotifierJob {

    @Autowired private JavaMailSender javaMailSender;
    @Autowired private ServerInstanceRepository serverInstanceRepository;
    @Autowired private SubscriberRepository subscriberRepository;

    @Value("${com.riz.exercise.application.email.sender}")
    private String emailSender;
    @Value("${com.riz.exercise.application.email.subject}")
    private String emailSubject;
    @Value("${com.riz.exercise.application.serviceEndPointUrl}")
    private String serviceEndPointUrl;

    RestTemplate restTemplate = new RestTemplate();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());



    /**
     * Main method.
     *
     * 1. Fetch SFDC server instances from local store
     * 2. Fetch SFDC server instances remotely via REST api call
     * 3. Update local store server instances with those fetched remotely and determine the delta
     * 4. Find the corresponding subscribers for those server instances with changed statuses
     * 5. Notify via email these subscribers with the statuses of the changed server instances
     */
    @Scheduled(cron = "${com.riz.exercise.application.scheduler.cron}")
    public void execute() {
        logger.info("Job started");
        Map<String, SFDCServerInstance> savedServerInstancesMap = getExistingServerInstances();
        List<SFDCServerInstance> currentServerInstances = getCurrentServerInstances(serviceEndPointUrl);
        List<SFDCServerInstance> updatedServerInstances = upsertChangedInstances(currentServerInstances, savedServerInstancesMap);
        Multimap<Subscriber, SFDCServerInstance> subscriberServerMap = getSubscriberServerMap(updatedServerInstances);
        sendEmails(subscriberServerMap);
        logger.info("Job completed");
    }

    /**
     * Fetches all server instances previously stored in the data store
     *
     * @return A Map; key = Server Instance name (e.g.: <code>NA16</code>), value = <code>SFDCServerInstance</code>
     *         object
     */
    private Map<String, SFDCServerInstance> getExistingServerInstances() {
        List<SFDCServerInstance> serverInstances = serverInstanceRepository.findAll();
        logger.info("Fetched {} instances from local repository", serverInstances.size());
        return Maps.uniqueIndex(serverInstances, si -> si.getKey());
    }

    /**
     * Fetch all server instances from salesforce.com via their REST service
     *
     * @return An array of SFDCServerInstance objects as translated from the REST service's JSON
     */
    private List<SFDCServerInstance> getCurrentServerInstances(String endPointUrl) {
        SFDCServerInstance[] serverInstancesArray = restTemplate.getForObject(endPointUrl, SFDCServerInstance[].class);
        logger.info("Fetched {} instances from remote call", serverInstancesArray.length);
        return Arrays.asList(serverInstancesArray);
    }

    /**
     * Compares the previously saved and current server instance lists. Updates those server instances whose statuses
     * have changed. Also updates any server instances new in the current list that were not previously present in the
     * existing list.
     *
     * @param remoteServerInstances The list of server instances as represented on salesforce.com via their REST service
     * @param localServerInstancesMap The list of server instances from our saved datastore
     * @return A list of server instances whose statuses have changed (doesn't include new server instances added)
     */
    private List<SFDCServerInstance> upsertChangedInstances(List<SFDCServerInstance> remoteServerInstances, Map<String, SFDCServerInstance> localServerInstancesMap) {
        remoteServerInstances.stream().forEach(remoteInstance -> {
            SFDCServerInstance localServerInstance = localServerInstancesMap.get(remoteInstance.getKey());
            // Insert (No localServerInstance found)
            if (localServerInstance == null) {
                serverInstanceRepository.save(remoteInstance);
                logger.debug("New instance:{} status:{} saved locally", remoteInstance.getKey(), remoteInstance.getStatus());
                return;
            }
            // Update (localServerInstance found)
            if (!localServerInstance.equals(remoteInstance)) {
                // Map the mongo id to indicate an existing record is to be updated
                remoteInstance.id = localServerInstance.id;
                serverInstanceRepository.save(remoteInstance);
                logger.debug("Updated instance: {} status from {} to {}", remoteInstance.getKey(), localServerInstance.getStatus(), remoteInstance.getStatus());
            }
        });

        List<SFDCServerInstance> updatedInstances = remoteServerInstances.stream().filter(si -> si.id != null).collect(Collectors.toList());
        logger.info("{} local instances updated", updatedInstances.size());
        return updatedInstances;
    }

    /**
     * Gets a map relating users/subscribers with the server instances that have changes.
     * <p>
     * For each server instance name, our local datastore is looked up and for all the users subscribing to that
     * specific server instance name.
     *
     * @param serverInstances
     * @return A map; key = <code>Subscriber</code>, value = <code>List<SFDCServerInstance></code> server instances,
     *         subscribed by the user, whose statuses have changed
     */
    private Multimap<Subscriber, SFDCServerInstance> getSubscriberServerMap(List<SFDCServerInstance> serverInstances) {
        Multimap<Subscriber, SFDCServerInstance> subscriberServerMap = ArrayListMultimap.create();
        serverInstances.stream().forEach(si -> {
            List<Subscriber> subscribers = subscriberRepository.findByServers(si.getKey());
            subscribers.stream().forEach(s -> subscriberServerMap.put(s, si));
        });
        return subscriberServerMap;
    }

    /**
     * Generates and sends and email to each user who has subscribed to one or more server instances whose status has
     * changed
     *
     * @param subscriberServerMap A map; key = <code>Subscriber</code>, value = <code>List<SFDCServerInstance></code>
     *        server instances subscribed by the user, whose statuses have changed
     */
    private void sendEmails(Multimap<Subscriber, SFDCServerInstance> subscriberServerMap) {
        subscriberServerMap.keySet().stream().forEach(subscriber -> sendEmail(subscriber, subscriberServerMap.get(subscriber)));
    }

    /**
     * Generates and sends an email to the user's email id listing those server instances who statuses have changed
     * and what their current status is.
     * <p>
     * @see #sendEmails(Multimap)
     *
     * @param subscriber The user who the email should be sent to
     * @param servers The server instances this user subscribes to whose statuses have changed
     */
    private void sendEmail(Subscriber subscriber, Collection<SFDCServerInstance> servers) {
        MimeMessage mail = javaMailSender.createMimeMessage();
        String subscriberFullName = subscriber.getFirstname() + " " + subscriber.getLastname();
        String emailMessageText = "";
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mail, true);
            helper.setReplyTo(emailSender);
            helper.setFrom(emailSender);
            helper.setSubject(emailSubject);
            helper.setTo(subscriber.getEmail());

            emailMessageText = generateEmailMessage(subscriberFullName, servers);
            helper.setText(emailMessageText);
        } catch (MessagingException e) {
            e.printStackTrace();
        } finally {
            logger.debug("Sending email message to '{}'<{}>:\n{}", subscriberFullName, subscriber.getEmail(), emailMessageText);
            javaMailSender.send(mail);
        }
    }

    /**
     * Helper method to return the email message string for a given user and all the server instance names that user
     * should be informed about
     *
     * @param name Full user name
     * @param servers A collection of server instances
     * @return String representing the email message body
     */
    private String generateEmailMessage(String name, Collection<SFDCServerInstance> servers) {
        List<String> messages = new ArrayList<String>() {{
            add("Hello " + name + ",");
            add("");
            add("There has been a change of status in " + servers.size() + " SFDC server instances you are subscribing to.");
            add("");
            add("Here are the current statuses of those servers that have changed:");
            add("");
            servers.stream().forEach(s -> add(s.getKey() + " -> " + s.getStatus()));
            add("");
            add("Thank you.");
        }};

        return Joiner.on("\r\n").join(messages);
    }
}
