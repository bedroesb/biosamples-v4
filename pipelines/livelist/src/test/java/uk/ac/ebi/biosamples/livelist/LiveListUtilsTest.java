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
package uk.ac.ebi.biosamples.livelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static uk.ac.ebi.biosamples.livelist.LiveListUtils.createLiveListString;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.biosamples.Application;
import uk.ac.ebi.biosamples.model.Sample;

@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest
@Import(Application.class)
public class LiveListUtilsTest {
  private String sampleJson =
      "{\"name\":\"MiSeq, Diospyros lotus L8 male flower 23 Apr MeGI promoter\",\"accession\":\"SAMD00046940\",\"domain\":\"123456789abcdef\",\"release\":\"2017-04-06T23:00:00Z\",\"update\":\"2018-07-25T09:28:16.841Z\",\"characteristics\":{\"Organism\":[{\"text\":\"Diospyros lotus\",\"ontologyTerms\":[\"55363\"]}],\"Sex\":[{\"text\":\"male\",\"ontologyTerms\":[\"http://purl.obolibrary.org/obo/PATO_0000384\"]}],\"colection date\":[{\"text\":\"2014-04-23\"}],\"cultivar\":[{\"text\":\"KK-L8\"}],\"model\":[{\"text\":\"Generic\"}],\"organism part\":[{\"text\":\"whole flower\"}],\"package\":[{\"text\":\"Generic.1.0\"}],\"synonym\":[{\"text\":\"TA_Meth-2\"}]},\"externalReferences\":[{\"url\":\"http://www.ebi.ac.uk/ena/data/view/SAMD00046940\"}],\"releaseDate\":\"2017-04-06\",\"updateDate\":\"2018-07-25\"}";

  @Autowired private ObjectMapper objectMapper;

  @Test
  public void given_a_sample_return_expected_live_list_entry() throws IOException {
    Sample sample = objectMapper.readValue(sampleJson, Sample.class);
    String liveListString = createLiveListString(sample);
    assertNotNull(liveListString);
    assertEquals(5, liveListString.split("\\|").length);
  }
}
