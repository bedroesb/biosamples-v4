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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.biosamples.exception.SampleNotFoundException;
import uk.ac.ebi.biosamples.model.Certificate;
import uk.ac.ebi.biosamples.model.Sample;
import uk.ac.ebi.biosamples.model.SubmittedViaType;
import uk.ac.ebi.biosamples.model.ga4gh.phenopacket.PhenopacketConverter;
import uk.ac.ebi.biosamples.service.*;
import uk.ac.ebi.biosamples.service.certification.CertifyService;
import uk.ac.ebi.biosamples.utils.LinkUtils;

/**
 * Primary controller for REST operations both in JSON and XML and both read and write.
 *
 * <p>See {@link SampleHtmlController} for the HTML equivalent controller.
 *
 * @author faulcon
 */
@RestController
@ExposesResourceFor(Sample.class)
@RequestMapping("/samples/{accession}")
@CrossOrigin
public class SampleRestController {
  private final SampleService sampleService;
  private final BioSamplesAapService bioSamplesAapService;
  private final SampleManipulationService sampleManipulationService;
  private final SampleResourceAssembler sampleResourceAssembler;
  private Ga4ghSampleToPhenopacketConverter phenopacketExporter;
  private PhenopacketConverter phenopacketConverter;
  private Logger log = LoggerFactory.getLogger(getClass());

  @Autowired private CertifyService certifyService;

  public SampleRestController(
      SampleService sampleService,
      BioSamplesAapService bioSamplesAapService,
      SampleManipulationService sampleManipulationService,
      SampleResourceAssembler sampleResourceAssembler,
      Ga4ghSampleToPhenopacketConverter phenopacketExporter,
      PhenopacketConverter phenopacketConverter) {
    this.sampleService = sampleService;
    this.bioSamplesAapService = bioSamplesAapService;
    this.sampleManipulationService = sampleManipulationService;
    this.sampleResourceAssembler = sampleResourceAssembler;
    this.phenopacketExporter = phenopacketExporter;
    this.phenopacketConverter = phenopacketConverter;
  }

  @PreAuthorize("isAuthenticated()")
  @CrossOrigin(methods = RequestMethod.GET)
  @GetMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
  public Resource<Sample> getSampleHal(
      @PathVariable String accession,
      @RequestParam(name = "legacydetails", required = false) String legacydetails,
      @RequestParam(name = "curationdomain", required = false) String[] curationdomain,
      @RequestParam(name = "curationrepo", required = false) String curationRepo) {
    log.trace("starting call");

    // decode percent-encoding from curation domains
    Optional<List<String>> decodedCurationDomains = LinkUtils.decodeTextsToArray(curationdomain);
    Optional<Boolean> decodedLegacyDetails;
    if ("true".equals(legacydetails)) {
      decodedLegacyDetails = Optional.of(Boolean.TRUE);
    } else {
      decodedLegacyDetails = Optional.empty();
    }

    // convert it into the format to return
    Optional<Sample> sample = sampleService.fetch(accession, decodedCurationDomains, curationRepo);
    if (sample.isEmpty()) {
      throw new SampleNotFoundException();
    }
    bioSamplesAapService.checkAccessible(sample.get());

    // TODO If user is not Read super user, reduce the fields to show
    if (decodedLegacyDetails.isPresent() && decodedLegacyDetails.get()) {
      sample = Optional.of(sampleManipulationService.removeLegacyFields(sample.get()));
    }

    // TODO cache control
    return sampleResourceAssembler.toResource(
        sample.get(), decodedLegacyDetails, decodedCurationDomains);
  }

  @RequestMapping(produces = "application/phenopacket+json")
  @PreAuthorize("isAuthenticated()")
  @CrossOrigin(methods = RequestMethod.GET)
  @GetMapping()
  public String getSamplePhenopacket(
      @PathVariable String accession,
      @RequestParam(name = "legacydetails", required = false) String legacydetails,
      @RequestParam(name = "curationdomain", required = false) String[] curationdomain,
      @RequestParam(name = "curationrepo", required = false) final String curationRepo) {
    log.trace("starting call");

    // decode percent-encoding from curation domains
    Optional<List<String>> decodedCurationDomains = LinkUtils.decodeTextsToArray(curationdomain);
    Optional<Boolean> decodedLegacyDetails;

    if ("true".equals(legacydetails)) {
      decodedLegacyDetails = Optional.of(Boolean.TRUE);
    } else {
      decodedLegacyDetails = Optional.empty();
    }

    // convert it into the format to return
    Optional<Sample> sample = sampleService.fetch(accession, decodedCurationDomains, curationRepo);

    if (sample.isEmpty()) {
      throw new SampleNotFoundException();
    }

    bioSamplesAapService.checkAccessible(sample.get());

    // TODO If user is not Read super user, reduce the fields to show
    if (decodedLegacyDetails.isPresent() && decodedLegacyDetails.get()) {
      sample = Optional.of(sampleManipulationService.removeLegacyFields(sample.get()));
    }

    return phenopacketConverter.convertToJsonPhenopacket(sample.get());
  }

  @PreAuthorize("isAuthenticated()")
  @CrossOrigin(methods = RequestMethod.GET)
  @GetMapping(produces = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
  public Sample getSampleXml(
      @PathVariable String accession,
      @RequestParam(name = "curationrepo", required = false) final String curationRepo) {
    Sample sample = this.getSampleHal(accession, "true", null, curationRepo).getContent();
    if (!sample.getAccession().matches("SAMEG\\d+")) {
      //			sample = Sample.build(sample.getName(),sample.getAccession(), sample.getDomain(),
      //					sample.getRelease(), sample.getUpdate(), sample.getCharacteristics(),
      // sample.getRelationships(),
      //					sample.getExternalReferences(), null, null, null);
      sample =
          Sample.Builder.fromSample(sample)
              .withNoOrganisations()
              .withNoPublications()
              .withNoContacts()
              .build();
    }

    // TODO cache control
    return sample;
  }

  //    @PreAuthorize("isAuthenticated()")
  //	  @CrossOrigin(methods = RequestMethod.GET)
  //    @GetMapping(produces = "application/ld+json")
  //    public JsonLDRecord getJsonLDSample(@PathVariable String accession) {
  //		Optional<Sample> sample = sampleService.fetch(accession);
  //		if (!sample.isPresent()) {
  //			throw new SampleNotFoundException();
  //		}
  //		bioSamplesAapService.checkAccessible(sample.get());
  //
  //        // check if the release date is in the future and if so return it as
  //        // private
  //        if (sample.get().getRelease().isAfter(Instant.now())) {
  //			throw new SampleNotAccessibleException();
  //        }
  //
  //		return jsonLDService.sampleToJsonLD(sample.get());
  //    }

  @PreAuthorize("isAuthenticated()")
  @PutMapping(consumes = {MediaType.APPLICATION_JSON_VALUE})
  public Resource<Sample> put(
      @PathVariable String accession,
      @RequestBody Sample sample,
      @RequestParam(name = "setfulldetails", required = false, defaultValue = "true")
          boolean setFullDetails)
      throws JsonProcessingException {
    final ObjectMapper jsonMapper = new ObjectMapper();
    List<Certificate> certificates = new ArrayList<>();

    if (sample.getAccession() == null || !sample.getAccession().equals(accession)) {
      throw new SampleAccessionMismatchException();
    }

    // todo Fix all integration tests to not to use predefined accessions, then remove
    // isIntegrationTestUser() check
    if (!sampleService.isExistingAccession(accession)
        && !(bioSamplesAapService.isWriteSuperUser()
            || bioSamplesAapService.isIntegrationTestUser())) {
      throw new SampleAccessionDoesNotExistException();
    }

    log.debug("Received PUT for " + accession);

    sample = bioSamplesAapService.handleSampleDomain(sample);

    if (sample.getData() != null && sample.getData().size() > 0) {
      if (bioSamplesAapService.isOriginalSubmitter(sample)) {
        sample = Sample.Builder.fromSample(sample).build();
      } else if (bioSamplesAapService.isWriteSuperUser()
          || bioSamplesAapService.isIntegrationTestUser()) {
        sample = Sample.Builder.fromSample(sample).build();
      } else {
        sample = Sample.Builder.fromSample(sample).withNoData().build();
      }
    }

    // update date is system generated field
    Instant update = Instant.now();

    SubmittedViaType submittedVia =
        sample.getSubmittedVia() == null ? SubmittedViaType.JSON_API : sample.getSubmittedVia();
    sample =
        Sample.Builder.fromSample(sample).withUpdate(update).withSubmittedVia(submittedVia).build();

    if (!accession.startsWith("SAMEG"))
      certificates = certifyService.certify(jsonMapper.writeValueAsString(sample), false);

    sample = Sample.Builder.fromSample(sample).withCertificates(certificates).build();

    final boolean isFirstTimeMetadataAdded = sampleService.beforeStore(sample);

    if (isFirstTimeMetadataAdded) {
      Instant now = Instant.now();

      sample = Sample.Builder.fromSample(sample).withSubmitted(now).build();
    }

    if (!setFullDetails) {
      log.trace("Removing contact legacy fields for " + accession);
      sample = sampleManipulationService.removeLegacyFields(sample);
    }

    sample = sampleService.store(sample, isFirstTimeMetadataAdded);

    // assemble a resource to return
    // create the response object with the appropriate status
    return sampleResourceAssembler.toResource(sample);
  }

  /*At this moment this patching is only for structured data*/
  @PreAuthorize("isAuthenticated()")
  @PatchMapping(consumes = {MediaType.APPLICATION_JSON_VALUE})
  public Resource<Sample> patchStructuredData(
      @PathVariable String accession,
      @RequestBody Sample sample,
      @RequestParam(name = "structuredData", required = false, defaultValue = "false")
          boolean structuredData) {

    if (!structuredData) throw new SampleDataPatchMethodNotSupportedException();

    if (sample.getAccession() == null || !sample.getAccession().equals(accession)) {
      throw new SampleAccessionMismatchException();
    }

    if (!sampleService.isExistingAccession(accession)
        && !(bioSamplesAapService.isWriteSuperUser()
            || bioSamplesAapService.isIntegrationTestUser())) {
      throw new SampleAccessionDoesNotExistException();
    }

    log.debug("Received PATCH for " + accession);

    sample = bioSamplesAapService.handleStructuredDataDomain(sample);
    sample = sampleService.storeSampleStructuredData(sample);

    return sampleResourceAssembler.toResource(sample);
  }

  @ResponseStatus(
      value = HttpStatus.BAD_REQUEST,
      reason = "Sample accession must match URL accession") // 400
  public static class SampleAccessionMismatchException extends RuntimeException {}

  @ResponseStatus(
      value = HttpStatus.METHOD_NOT_ALLOWED,
      reason = "Pass argument structuredData=true if you want to PATCH data to sample") // 400
  public static class SampleDataPatchMethodNotSupportedException extends RuntimeException {}

  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Sample accession does not exist") // 400
  public static class SampleAccessionDoesNotExistException extends RuntimeException {}
}
