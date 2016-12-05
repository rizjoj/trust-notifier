package com.riz.exercise.persistence;

import com.riz.exercise.model.SFDCServerInstance;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Created by rizjoj on 12/1/16.
 */
public interface ServerInstanceRepository extends MongoRepository<SFDCServerInstance, String> {
    List<SFDCServerInstance> findByKey(String key);
}
