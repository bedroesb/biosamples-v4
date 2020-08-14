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
package uk.ac.ebi.biosamples;

import static org.hamcrest.Matchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.service.BioSamplesAapService;
import uk.ac.ebi.biosamples.service.SampleService;
import uk.ac.ebi.biosamples.service.SchemaValidatorService;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StructuredDataTest {

  @Autowired private MockMvc mockMvc;

  private JacksonTester<Sample> json;

  private ObjectMapper mapper;

  @MockBean private BioSamplesAapService bioSamplesAapService;

  @MockBean private SampleService sampleService;

  @MockBean private SchemaValidatorService schemaValidatorService;

  @Before
  public void init() {
    mapper = new ObjectMapper();
  }

  @Test
  public void able_to_submit_sample_with_structuredData() throws Exception {
    String json =
        StreamUtils.copyToString(
            new ClassPathResource("structured_data_sample.json").getInputStream(),
            Charset.defaultCharset());
    Sample sample = mapper.readValue(json, Sample.class);
    Assert.assertEquals(3, sample.getData().size());
  }
}
