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
package uk.ac.ebi.biosamples.mongo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MongoProperties {

  @Value("${biosamples.mongo.sample.writeConcern:0}")
  private String sampleWriteConcern;

  @Value("${biosamples.mongo.submission.writeConcern:0}")
  private String submissionWriteConcern;

  @Value("${biosamples.accession.prefix:SAMEA}")
  private String accessionPrefix;

  @Value("${biosamples.accession.min:100000}")
  private int accessionMinimum;

  @Value("${biosamples.accession.queuesize:100}")
  private int accessionQueueSize;

  public String getAccessionPrefix() {
    return accessionPrefix;
  }

  public int getAccessionMinimum() {
    return accessionMinimum;
  }

  public int getAcessionQueueSize() {
    return accessionQueueSize;
  }

  public String getSampleWriteConcern() {
    return sampleWriteConcern;
  }

  public String getSubmissionWriteConcern() {
    return submissionWriteConcern;
  }
}
