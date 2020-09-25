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
package uk.ac.ebi.biosamples.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.biosamples.service.certification.CertifyService;

@RestController
@RequestMapping(value = "/certificates", produces = MediaType.APPLICATION_JSON_VALUE)
public class CertificateRetrievalController {
  private Logger log = LoggerFactory.getLogger(getClass());
  @Autowired private CertifyService certifyService;

  @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<String> getCertificate(
      @RequestParam(value = "certificateName") String certificateName) throws IOException {
    log.info("Fetching certificate with name " + certificateName);

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    JsonParser jp = new JsonParser();
    JsonElement je =
        jp.parse(certifyService.getCertificateFileContentByCertificateName(certificateName));
    String prettyJsonString = gson.toJson(je);

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\""
                + certifyService.getCertificateFileNameByCertificateName(certificateName)
                + "\"")
        .body(prettyJsonString);
  }
}
