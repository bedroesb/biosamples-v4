/*
* Copyright 2019 EMBL - European Bioinformatics Institute
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
* file except in compliance with the License. You may obtain a copy of the License at
* http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the
* License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
* CONDITIONS OF ANY KIND, either express or implied. See the License for the
* specific language governing permissions and limitations under the License.
*/
package uk.ac.ebi.biosamples.curatedview;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.Resource;
import uk.ac.ebi.biosamples.client.BioSamplesClient;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.model.StaticViewWrapper;
import uk.ac.ebi.biosamples.mongo.model.MongoSample;
import uk.ac.ebi.biosamples.mongo.repo.MongoSampleRepository;
import uk.ac.ebi.biosamples.mongo.service.SampleToMongoSampleConverter;

public class CuratedViewCallable implements Callable<Void> {
  private static final Logger LOG = LoggerFactory.getLogger(CuratedViewCallable.class);

  private final String accession;
  private final MongoSampleRepository mongoSampleRepository;
  private final SampleToMongoSampleConverter sampleToMongoSampleConverter;
  private final BioSamplesClient bioSamplesClient;
  static final ConcurrentLinkedQueue<String> failedQueue = new ConcurrentLinkedQueue<>();

  CuratedViewCallable(
      String accession,
      MongoSampleRepository mongoSampleRepository,
      SampleToMongoSampleConverter sampleToMongoSampleConverter,
      BioSamplesClient bioSamplesClient) {
    this.accession = accession;
    this.mongoSampleRepository = mongoSampleRepository;
    this.sampleToMongoSampleConverter = sampleToMongoSampleConverter;
    this.bioSamplesClient = bioSamplesClient;
  }

  @Override
  public Void call() {
    persistSamplesToStaticViewCollection();
    return null;
  }

  private void persistSamplesToStaticViewCollection() {
    Optional<Resource<Sample>> optionalResource = bioSamplesClient.fetchSampleResource(accession);
    if (optionalResource.isPresent()) {
      Sample sample = optionalResource.get().getContent();
      MongoSample mongoSample = sampleToMongoSampleConverter.convert(sample);
      mongoSampleRepository.insertSampleToCollection(
          mongoSample, StaticViewWrapper.StaticView.SAMPLES_CURATED);
    } else {
      LOG.warn("Failed to read sample from service, accession: {}", accession);
      failedQueue.add(accession);
    }
  }
}
