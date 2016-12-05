package com.riz.exercise.persistence;

import com.riz.exercise.model.Subscriber;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Created by rizjoj on 12/1/16.
 */
public interface SubscriberRepository extends MongoRepository<Subscriber, String> {
    List<Subscriber> findByServers(String server);
    List<Subscriber> findByEmail(String email);
}
