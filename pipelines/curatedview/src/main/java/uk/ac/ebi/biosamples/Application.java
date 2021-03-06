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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableCaching
public class Application {

  public static void main(String[] args) {
    SpringApplication.exit(SpringApplication.run(Application.class, args));
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateCustomizer restTemplateCustomizer) {
    RestTemplate restTemplate = new RestTemplate();
    restTemplateCustomizer.customize(restTemplate);
    return restTemplate;
  }

  @Bean
  public RestTemplateCustomizer restTemplateCustomizer(
      BioSamplesProperties bioSamplesProperties, PipelinesProperties pipelinesProperties) {
    return new PipelinesHelper()
        .getRestTemplateCustomizer(bioSamplesProperties, pipelinesProperties);
  }
}
